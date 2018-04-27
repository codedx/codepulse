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

import akka.actor.{ Actor, Stash }
import com.secdec.codepulse.data.bytecode.{ AsmVisitors, CodeForestBuilder, CodeTreeNodeKind }
import com.secdec.codepulse.data.jsp.{ JasperJspAdapter, JspAnalyzer }
import com.secdec.codepulse.data.model.{ SourceDataAccess, TreeNodeDataAccess, TreeNodeImporter }
import com.secdec.codepulse.data.storage.Storage
import com.secdec.codepulse.events.GeneralEventBus
import com.secdec.codepulse.input.{ CanProcessFile, LanguageProcessor }
import com.secdec.codepulse.processing.{ ProcessEnvelope, ProcessStatus }
import com.secdec.codepulse.processing.ProcessStatus.{ DataInputAvailable, ProcessDataAvailable }
import com.secdec.codepulse.util.SmartLoader.Success
import com.secdec.codepulse.util.{ SmartLoader, ZipEntryChecker }
import org.apache.commons.io.FilenameUtils

class ByteCodeProcessor(eventBus: GeneralEventBus) extends Actor with Stash with LanguageProcessor {
	val group = "Classes"
	val traceGroups = (group :: CodeForestBuilder.JSPGroupName :: Nil).toSet

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
		storage.find() { (filename, entry, contents) =>
			!entry.isDirectory && FilenameUtils.getExtension(entry.getName) == "class"
		}
	}

	def process(storage: Storage, treeNodeData: TreeNodeDataAccess, sourceData: SourceDataAccess): Unit = {
		val RootGroupName = "Classes"
		val tracedGroups = (RootGroupName :: CodeForestBuilder.JSPGroupName :: Nil).toSet
		val builder = new CodeForestBuilder
		val methodCorrelationsBuilder = collection.mutable.Map.empty[String, Int]

		//TODO: make this configurable somehow
		val jspAdapter = new JasperJspAdapter

		val loader = SmartLoader

		storage.readEntries() { (filename, entry, contents) =>
			val groupName = if (filename == storage.name) RootGroupName else s"JARs/${filename substring storage.name.length + 1}"
			if (!entry.isDirectory) {
				FilenameUtils.getExtension(entry.getName) match {
					case "class" =>
						val methods = AsmVisitors.parseMethodsFromClass(contents)
						for {
							(name, size) <- methods
							treeNode <- builder.getOrAddMethod(groupName, name, size, entry.getName)
						} methodCorrelationsBuilder += (name -> treeNode.id)

					case "jsp" =>
						val jspContents = loader loadStream contents
						jspContents match {
							case Success(content, _) =>
								val jspSize = JspAnalyzer analyze content
								jspAdapter.addJsp(entry.getName, jspSize)
							case _ =>
						}

					case _ => // nothing
				}
			} else if (entry.getName.endsWith("WEB-INF/")) {
				jspAdapter addWebinf entry.getName
			}
		}

		sourceData.importSourceFiles(builder.sourceFiles)

		val jspCorrelations = jspAdapter build builder
		val treemapNodes = builder.condensePathNodes().result
		val methodCorrelations = methodCorrelationsBuilder.result

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

			treeNodeData.mapJsps(jspCorrelations)
			treeNodeData.mapMethodSignatures(methodCorrelations)
		}
	}
}
