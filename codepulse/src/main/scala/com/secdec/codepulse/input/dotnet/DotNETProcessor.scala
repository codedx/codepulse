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

package com.secdec.codepulse.input.dotnet

import java.io.File

import akka.actor.{ Actor, Stash }
import com.secdec.codepulse.data.bytecode.{ CodeForestBuilder, CodeTreeNodeKind }
import com.secdec.codepulse.data.dotnet.{ DotNet, SymbolReaderHTTPServiceConnector }
import com.secdec.codepulse.data.model.{ TreeNodeDataAccess, TreeNodeImporter }
import com.secdec.codepulse.events.GeneralEventBus
import com.secdec.codepulse.input.{ CanProcessFile, LanguageProcessor }
import com.secdec.codepulse.processing.{ ProcessEnvelope, ProcessStatus }
import com.secdec.codepulse.processing.ProcessStatus.{ DataInputAvailable, ProcessDataAvailable }
import com.secdec.codepulse.util.ZipEntryChecker
import org.apache.commons.io.FilenameUtils

class DotNETProcessor(eventBus: GeneralEventBus) extends Actor with Stash with LanguageProcessor {
	val group = "dotNet"
	val traceGroups = (group :: Nil).toSet

	def receive = {
		case ProcessEnvelope(_, DataInputAvailable(identifier, file, treeNodeData)) => {
			try {
				if(canProcess(file)) {
					eventBus.publish(ProcessStatus.Running(identifier))
//					var builder =
					process(file, treeNodeData)
					println("dotNET Processor is making data available")
					eventBus.publish(ProcessDataAvailable(identifier, file, treeNodeData))
				}
			} catch {
				case exception: Exception => eventBus.publish(ProcessStatus.Failed(identifier, "dotNET Processor", Some(exception)))
			}
		}

		case CanProcessFile(file) => {
			sender ! canProcess(file)
		}
	}

	def canProcess(file: File): Boolean = {
		ZipEntryChecker.findFirstEntry(file) { (_, entry, _) =>
			val extension = FilenameUtils.getExtension(entry.getName)
			!entry.isDirectory && (extension == "exe" || extension == "dll")
		}
	}

	def process(file: File, treeNodeData: TreeNodeDataAccess): Unit = {
//		val RootGroupName = "dotNET"
//		val tracedGroups = (RootGroupName :: Nil).toSet
		val builder = new CodeForestBuilder
		val methodCorrelationsBuilder = collection.mutable.Map.empty[String, Int]
		val dotNETAssemblyFinder = DotNet.AssemblyPairFromZip(file) _

		ZipEntryChecker.forEachEntry(file) { (filename, entry, contents) =>
			val groupName = if (filename == file.getName) group else s"Assemblies/${filename substring file.getName.length + 1}"

			if (!entry.isDirectory) {
				dotNETAssemblyFinder(entry) match {
					case Some((assembly, symbols)) => {
						val methods = new SymbolReaderHTTPServiceConnector(assembly, symbols).Methods
						for {
							(sig, size) <- methods
							treeNode <- Option(builder.getOrAddMethod(groupName, sig, size))
						} methodCorrelationsBuilder += (s"${sig.containingClass}.${sig.name};${sig.modifiers};(${sig.params mkString ","});${sig.returnType}" -> treeNode.id)
					}

					case _ => // nothing
				}
			}
		}

		val treemapNodes = builder.condensePathNodes().result
		val methodCorrelations = methodCorrelationsBuilder.result

		if (treemapNodes.isEmpty) {
			throw new NoSuchElementException("No method data found in analyzed upload file.")
		} else {
			val importer = TreeNodeImporter(treeNodeData)

			importer ++= treemapNodes.toIterable map {
				case (root, node) =>
					node -> (node.kind match {
						case CodeTreeNodeKind.Grp | CodeTreeNodeKind.Pkg => Some(traceGroups contains root.name)
						case _ => None
					})
			}

			importer.flush

			treeNodeData.mapMethodSignatures(methodCorrelations)
		}

//		builder
	}
}
