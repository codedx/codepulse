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

import scala.collection.mutable.{ HashMap, MultiMap, Set }

import akka.actor.{ Actor, Stash }
import com.secdec.codepulse.data.bytecode.{ AsmVisitors, CodeForestBuilder, CodeTreeNodeKind }
import com.secdec.codepulse.data.jsp.{ JasperJspAdapter, JspAnalyzer }
import com.secdec.codepulse.data.model.{ MethodSignatureNode, SourceDataAccess, TreeNodeDataAccess, TreeNodeImporter }
import com.secdec.codepulse.data.storage.Storage
import com.secdec.codepulse.events.GeneralEventBus
import com.secdec.codepulse.input.pathnormalization.{ FilePath, NestedPath, PathNormalization }
import com.secdec.codepulse.input.{ CanProcessFile, LanguageProcessor }
import com.secdec.codepulse.processing.{ ProcessEnvelope, ProcessStatus }
import com.secdec.codepulse.processing.ProcessStatus.{ DataInputAvailable, ProcessDataAvailable }
import com.secdec.codepulse.util.SmartLoader.Success
import com.secdec.codepulse.util.SmartLoader
import org.apache.commons.io.FilenameUtils
import net.liftweb.common.Loggable

class ByteCodeProcessor(eventBus: GeneralEventBus) extends Actor with Stash with LanguageProcessor with Loggable {
	val group = "Classes"
	val traceGroups = (group :: CodeForestBuilder.JSPGroupName :: Nil).toSet
	val sourceExtensions = List("java", "jsp")

	def receive = {
		case ProcessEnvelope(_, DataInputAvailable(identifier, storage, treeNodeData, sourceData, post)) => {
			try {
				if(canProcess(storage)) {
					process(storage, treeNodeData, sourceData)
					post()
					eventBus.publish(ProcessDataAvailable(identifier, storage, treeNodeData, sourceData))
				}
			} catch {
				case exception: Exception => eventBus.publish(ProcessStatus.asEnvelope(ProcessStatus.Failed(identifier, "Java ByteCode Processor", Some(exception))))
			}
		}

		case CanProcessFile(file) => {
			Storage(file) match {
				case Some(storage) => sender ! canProcess(storage)
				case _ => sender ! false
			}
		}
	}

	def canProcess(storage: Storage): Boolean = {
		storage.find() { (filename, entryPath, entry, contents) =>
			!entry.isDirectory && FilenameUtils.getExtension(entry.getName) == "class"
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
						val methods = AsmVisitors.parseMethodsFromClass(contents)
						for {
							(file, name, size, lineCount) <- methods
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
							treeNode <- builder.getOrAddMethod(groupName, name, size, authority, Option(lineCount))
						} {
							methodCorrelationsBuilder += (name -> treeNode.id)
						}

					case "jsp" =>
						val jspContents = loader loadStream contents
						jspContents match {
							case Success(content, _) =>
								val jspSize = JspAnalyzer analyze content

								val nestedPath = entryPath match {
									case Some(ep) => new NestedPath(ep.paths :+ new FilePath(entry.getName, None))
									case None => new NestedPath(List(new FilePath(entry.getName, None)))
								}

								jspAdapter.addJsp(nestedPath.toString, jspSize)
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
}
