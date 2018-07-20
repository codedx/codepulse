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

import java.io.InputStream
import scala.collection.mutable.{ HashMap, MultiMap, Set }
import scala.util.{Try, Success, Failure}

import com.secdec.codepulse.data.bytecode.{ AsmVisitors, CodeForestBuilder, CodeTreeNodeKind }
import com.secdec.codepulse.data.jsp.{ JasperJspAdapter, JspAnalyzer }
import com.secdec.codepulse.data.model.{ MethodSignatureNode, SourceDataAccess, TreeNodeDataAccess, TreeNodeImporter }
import com.secdec.codepulse.data.storage.Storage
import com.secdec.codepulse.input.pathnormalization.{ FilePath, NestedPath, PathNormalization }
import com.secdec.codepulse.input.LanguageProcessor
import com.secdec.codepulse.util.SmartLoader.{Success => S}
import com.secdec.codepulse.util.SmartLoader
import org.apache.commons.io.FilenameUtils
import net.liftweb.common.Loggable
import org.apache.commons.io.input.CloseShieldInputStream
import com.github.javaparser.JavaParser
import com.github.javaparser.ParseException
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.secdec.codepulse.data.bytecode.parse.JavaSourceParsing
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

		def getMethodAndStart(mi: MethodInfo, ci: ClassInfo): (String, Int) = {
			(ci.signature.name.slashedName + "." + mi.signature.name, mi.lines.start.line)
		}

		def flattenToMethods(ls: List[ClassInfo]): List[(String, Int)] = {
			ls match {
				case Nil => Nil
				case l => l.flatMap(ci => ci.memberMethods.map(mi => getMethodAndStart(mi, ci))) ::: l.flatMap(ci => flattenToMethods(ci.memberClasses))
			}
		}

		val sourceMethods = new HashMap[String, List[(String, Int)]]
		storage.readEntries(sourceType("java") _) { (filename, entryPath, entry, contents) =>
			val hierarchy = JavaSourceParsing.tryParseJavaSource(new CloseShieldInputStream(contents))
			val methodsAndStarts = hierarchy match {
				case Success(h) => flattenToMethods(h)
				case Failure(_) => List.empty[(String, Int)]
			}
//			val x = 3
//			val methodsAndStarts = parseJava9(contents)
			sourceMethods.get(entry.getName()) match {
				case None => sourceMethods += (entry.getName() -> methodsAndStarts)
				case Some(entries) => sourceMethods += (entry.getName() -> (entries ::: methodsAndStarts))
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
			if (!entry.isDirectory) {
				FilenameUtils.getExtension(entry.getName) match {
					case "class" =>
						val methods = AsmVisitors.parseMethodsFromClass(new CloseShieldInputStream(contents))
						for {
							(file, name, size, lineCount, methodStartLine) <- methods
							pkg = getPackageFromSig(name)
							filePath = FilePath(Array(pkg, file).mkString("/"))
							nestedPath = filePath.map(fp => entryPath match {
								case Some(ep) => new NestedPath(ep.paths :+ fp)
								case None => new NestedPath(List(fp))
							})
							authority = nestedPath match {
								case Some(np) => authoritativePath(groupName, np).map (_.toString)
								case None => None
							}
							methodsAndStartLines = authority.flatMap(sourceMethods.get)
							startLine = methodsAndStartLines.flatMap { l =>
								val potentials = l.filter { case (qualifiedName, line) => qualifiedName == name.split(";").take(1)(0) }
								val selection = potentials.headOption//.getOrElse((("", 0)))
								selection.map(_._2)
								//Some(selection._2)
							}
							treeNode <- builder.getOrAddMethod(groupName, name, size, authority, Option(lineCount), startLine)
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

					case _ => // nothing
				}
			} else if (entry.getName.endsWith("WEB-INF/")) {
				jspAdapter addWebinf entry.getName
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

	private def parseJava9(contents: InputStream): List[(String, Int)] = {
		def mkString(s1: String, s2: String, sep: String): String = {
			if(s1.isEmpty) s2
			else if(s2.isEmpty) s1
			else s1 + sep + s2
		}

		def getQualifiedClass(n: Node, s: String): String = {
			n match {
				case c: ConstructorDeclaration => if(c.getParentNode().isPresent()) {
					getQualifiedClass(c.getParentNode().get(), s)
				} else {
					s
				}
				case m: MethodDeclaration => if(m.getParentNode().isPresent()) {
					getQualifiedClass(m.getParentNode().get(), s)
				} else {
					s
				}
				case c: ClassOrInterfaceDeclaration => if(c.getParentNode().isPresent()) {
					getQualifiedClass(c.getParentNode().get(), mkString(c.getName().toString, s, "$"))//c.getName + "." + s)
				} else {
					s
				}
				case cu: CompilationUnit => if(cu.getPackageDeclaration().isPresent()) {
					getQualifiedClass(cu.getPackageDeclaration().get(), s)
				} else {
					s
				}
				case p: PackageDeclaration => mkString(p.getName().toString.replace(".", "/"), s, "/")//p.getName + "." + s
			}
		}

		var methodStarts = List[(String, Int)]()

		try {
			val sourceContent = new CloseShieldInputStream(contents)
			val cu = JavaParser.parse(sourceContent)

			val methodVisitor = new VoidVisitorAdapter[Void] {
				override def visit(n: ConstructorDeclaration, arg: Void): Unit = {
					super.visit(n, arg)
					System.out.println(getQualifiedClass(n, "") + "\t" + n.getName() + "\t" + n.getBegin())

					val qualifiedName = mkString(getQualifiedClass(n, ""), n.getName().toString, ".")
					val startLine = if(n.getBegin().isPresent()) {
						val location = n.getBegin().get()
						location.line
					} else {
						0
					}

					methodStarts = (qualifiedName, startLine) :: methodStarts
				}

				override def visit(n: MethodDeclaration, arg: Void): Unit = {
					super.visit(n, arg)
					System.out.println(getQualifiedClass(n, "") + "\t" + n.getName() + "\t" + n.getBegin())

					val qualifiedName = mkString(getQualifiedClass(n, ""), n.getName().toString, ".")
					val startLine = if(n.getBegin().isPresent()) {
						val location = n.getBegin().get()
						location.line
					} else {
						0
					}

					methodStarts = (qualifiedName, startLine) :: methodStarts
				}
			}

			cu.accept(methodVisitor, null)
		} catch {
			case ex: Exception => System.out.println(ex)
		}

		methodStarts
	}
}
