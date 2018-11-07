/*
 * Code Pulse: A real-time code coverage testing tool. For more information
 * see http://code-pulse.com
 *
 * Copyright (C) 2017 Applied Visions - http://securedecisions.avi.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.secdec.codepulse.input.bytecode

import java.io.File
import scala.collection.mutable.{ HashMap, MultiMap, Set }
import scala.util.{ Failure, Success, Try }

import com.secdec.codepulse.data.bytecode.{ AsmVisitors, CodeForestBuilder, CodeTreeNodeKind }
import com.secdec.codepulse.data.jsp.{ JasperJspAdapter, JspAnalyzer }
import com.secdec.codepulse.data.model.{ MethodSignatureNode, SourceDataAccess, TreeNodeDataAccess, TreeNodeImporter }
import com.secdec.codepulse.data.storage.Storage
import com.secdec.codepulse.input.pathnormalization.{ FilePath, NestedPath, PathNormalization }
import com.secdec.codepulse.input.LanguageProcessor
import com.secdec.codepulse.util.SmartLoader.{ Success => S }
import com.secdec.codepulse.util.SmartLoader
import org.apache.commons.io.FilenameUtils
import net.liftweb.common.Loggable
import org.apache.commons.io.input.CloseShieldInputStream
import com.secdec.codepulse.data.bytecode.parse.{ JavaBinaryMethodSignature, JavaSourceParsing, MethodSignature }
import com.secdec.codepulse.data.bytecode.parse.JavaSourceParsing.{ ClassInfo, MethodInfo }

class ByteCodeProcessor() extends LanguageProcessor with Loggable {
	val group = "Classes"
	val traceGroups = (group :: CodeForestBuilder.JSPGroupName :: Nil).toSet
	val sourceExtensions = List("java", "jsp")

	def canProcess(storage: Storage): Boolean = {
		storage.find() { (filename, entryPath, entry, contents) =>
			!entry.isDirectory && (FilenameUtils.getExtension(entry.getName) == "class" || FilenameUtils.getExtension(entry.getName) == "jsp")
		}
	}

	def process(storage: Storage, treeNodeData: TreeNodeDataAccess, sourceData: SourceDataAccess): Unit = {
		val RootGroupName = "Classes"
		val tracedGroups = (RootGroupName :: CodeForestBuilder.JSPGroupName :: Nil).toSet
		val builder = new CodeForestBuilder
		val methodCorrelationsBuilder = collection.mutable.ListBuffer.empty[(String, Int)]

		//TODO: make this configurable somehow
		val jspAdapter = new JasperJspAdapter

		val loader = SmartLoader
		val pathStore = new HashMap[(String, String), Set[Option[NestedPath]]] with MultiMap[(String, String), Option[NestedPath]]

		storage.readEntries(sourceFiles _) { (filename, entryPath, entry, contents) =>
			val entryFilePath = FilePath(entry.getName)
			val groupName = if (filename == storage.name) RootGroupName else s"JARs/${filename substring storage.name.length + 1}"
			entryFilePath.foreach(efp => {
				entryPath match {
					case Some(ep) => pathStore.addBinding((groupName, efp.name), Some(new NestedPath(ep.paths :+ efp)))
					case None => pathStore.addBinding((groupName, efp.name), Some(new NestedPath(List(efp))))
				}
			})
		}

		def signatureAsString(signature: MethodSignature): String = {
			signature.name + ";" + signature.simplifiedReturnType.name + ";" + signature.simplifiedArgumentTypes.map(_.name).mkString(";")
		}

		def getMethodAndRange(mi: MethodInfo, ci: ClassInfo): (String, Int, Int) = {
			(ci.signature.name.slashedName + "." + signatureAsString(mi.signature), mi.lines.start, mi.lines.end)
		}

		def flattenToMethods(ls: List[ClassInfo]): List[(String, Int, Int)] = {
			ls match {
				case Nil => Nil
				case l => l.flatMap(ci => ci.memberMethods.map(mi => getMethodAndRange(mi, ci))) ::: l.flatMap(ci => flattenToMethods(ci.memberClasses))
			}
		}

		val sourceMethods = new HashMap[String, List[(String, Int, Int)]]
		storage.readEntries(sourceType("java") _) { (filename, entryPath, entry, contents) =>
			val hierarchy = JavaSourceParsing.tryParseJavaSource(new CloseShieldInputStream(contents))
			val methodsAndStarts = hierarchy match {
				case Success(h) => flattenToMethods(h)
				case Failure(_) => List.empty[(String, Int, Int)]
			}

			val authority = entryPath.flatMap(ep =>
				Option(Array(ep.toString, entry.getName).mkString(";"))
			).getOrElse(entry.getName)

			sourceMethods.get(authority) match {
				case None => sourceMethods += (authority -> methodsAndStarts)
				case Some(entries) => sourceMethods += (authority -> (entries ::: methodsAndStarts))
			}
		}

		def getPackageFromSig(sig: String): String = {
			val packageContainer = sig.split(";").take(1)
			val packagePart = packageContainer(0).split("/").dropRight(1)

			packagePart.mkString("/")
		}

		def authoritativePath(group: String, filePath: NestedPath): Option[NestedPath] = {
			pathStore.get((group, filePath.paths.last.name)) match {
				case None => None
				case Some(fps) => {
					fps.flatten.find { authority => PathNormalization.isLocalizedInAuthorityPath(authority, filePath) }
				}
			}
		}

		storage.readEntries() { (filename, entryPath, entry, contents) =>
			val groupName = if (filename == storage.name) RootGroupName else s"JARs/${filename substring storage.name.length + 1}"
				FilenameUtils.getExtension(entry.getName) match {
					case "class" =>
						val methods = AsmVisitors.parseMethodsFromClass(new CloseShieldInputStream(contents))
						for {
							(file, name, size, lineCount) <- methods
							pkg = getPackageFromSig(name)
							filePath = FilePath(Array(pkg, file).mkString("/"))
							nestedPath = filePath.map(fp => entryPath match {
								case Some(ep) => new NestedPath(ep.paths :+ fp)
								case None => new NestedPath(List(fp))
							})
							authority = nestedPath.flatMap(np => authoritativePath(groupName, np).map (_.toString))
							methodsAndRanges = authority.flatMap(sourceMethods.get)
							Array(nameStr, accessStr, rawSignature) = name.split(";", 3)
							binaryMethodSignature = Try(JavaBinaryMethodSignature.parseMemoV1(String.join(";", accessStr, nameStr, rawSignature)))
							startLine = binaryMethodSignature.toOption.flatMap(bms =>
								methodsAndRanges.flatMap { l =>
									val potentials = l.filter { case (qualifiedName, _, _) => qualifiedName == signatureAsString(bms) }
									val selection = potentials.headOption
									val start = selection.map(_._2)
									start
								})
							endLine = binaryMethodSignature.toOption.flatMap(bms =>
								methodsAndRanges.flatMap { l =>
									val potentials = l.filter { case (qualifiedName, _, _) => qualifiedName == signatureAsString(bms) }
									val selection = potentials.headOption
									val end = selection.map(_._3)
									end
								})
							treeNode <- builder.getOrAddMethod(groupName, name, size, authority, Option(lineCount), startLine, endLine, None)
						} {
							methodCorrelationsBuilder += (name -> treeNode.id)
						}

					case "jsp" =>
						val jspContents = loader loadStream contents
						jspContents match {
							case S(content, _) =>
								val jspSize = JspAnalyzer analyze content

								val entryName = entry.getName
								val nestedPath = entryPath match {
									case Some(ep) => new NestedPath(ep.paths :+ new FilePath(entryName, None))
									case None => new NestedPath(List(new FilePath(entryName, None)))
								}

								jspAdapter.addJsp(nestedPath.toString, entryName, jspSize.approximateInstructionCount, jspSize.totalLineCount)
							case _ =>
						}

					case "xml" =>
						if (entry.getName.toLowerCase.endsWith("web-inf/web.xml")) {
							val web = new File(entry.getName)
							val webInf = web.getParent
							jspAdapter addWebinf webInf
						}

					case _ => // nothing
			}
		}

		val jspCorrelations = jspAdapter build builder

		val treemapNodes = builder.condensePathNodes().result
		val methodCorrelations = methodCorrelationsBuilder.result

		sourceData.importSourceFiles(builder.sourceFiles.map((x:((String,String),Int)) => x._2 -> x._1._2))

		if (treemapNodes.isEmpty) {
			throw new NoSuchElementException("No method data found in analyzed upload file.")
		} else {
			val importer = TreeNodeImporter(treeNodeData)

			importer ++= treemapNodes.toIterable map {
				case (root, node) =>
					node -> (node.kind match {
						case CodeTreeNodeKind.Grp | CodeTreeNodeKind.Pkg => Some(tracedGroups contains root.name)
						case _ => None
					})
			}

			importer.flush

			val jspCorrelationsDuplicatePaths = jspCorrelations.groupBy(identity).collect { case (x, List(_,_,_*)) => x }
			jspCorrelationsDuplicatePaths.keys.foreach { path => {
				logger.warn(s"An input file cannot contain duplicate JSP paths. Tracing for JSP path '$path' will not work correctly.")
			}}

			treeNodeData.mapJsps(if (jspCorrelationsDuplicatePaths.isEmpty) jspCorrelations else jspCorrelations.toMap)
			treeNodeData.mapMethodSignatures(methodCorrelations.map { case (signature, nodeId) => MethodSignatureNode(0, signature, nodeId) })
		}
	}
}
