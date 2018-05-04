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
import scala.collection.mutable.{ HashMap, Set, MultiMap }

import akka.actor.{ Actor, Stash }
import com.secdec.codepulse.data.bytecode.{ CodeForestBuilder, CodeTreeNodeKind }
import com.secdec.codepulse.data.dotnet.{ DotNet, SymbolReaderHTTPServiceConnector, SymbolService }
import com.secdec.codepulse.data.model.{ SourceDataAccess, TreeNodeDataAccess, TreeNodeImporter }
import com.secdec.codepulse.data.storage.Storage
import com.secdec.codepulse.events.GeneralEventBus
import com.secdec.codepulse.input.{ CanProcessFile, LanguageProcessor }
import com.secdec.codepulse.processing.{ ProcessEnvelope, ProcessStatus }
import com.secdec.codepulse.processing.ProcessStatus.{ DataInputAvailable, ProcessDataAvailable }
import com.secdec.codepulse.input.pathnormalization.{ FilePath, PathNormalization }
import com.secdec.codepulse.util.ZipEntryChecker
import org.apache.commons.io.FilenameUtils

class DotNETProcessor(eventBus: GeneralEventBus) extends Actor with Stash with LanguageProcessor {
	val group = "Classes"
	val traceGroups = (group :: Nil).toSet
	val sourceExtensions = List("cs", "vb", "fs", "fsi", "fsx", "fsscript", "cpp")

	val symbolService = new SymbolService
	symbolService.create

	def receive = {
		case ProcessEnvelope(_, DataInputAvailable(identifier, storage, treeNodeData, sourceData, post)) => {
			try {
				if(canProcess(storage)) {
					process(storage, treeNodeData, sourceData)
					post()
					eventBus.publish(ProcessDataAvailable(identifier, storage, treeNodeData, sourceData))
				}
			} catch {
				case exception: Exception => eventBus.publish(ProcessStatus.Failed(identifier, "dotNET Processor", Some(exception)))
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
		storage.find() { (_, entry, _) =>
			val extension = FilenameUtils.getExtension(entry.getName)
			!entry.isDirectory && (extension == "exe" || extension == "dll")
		}
	}

	def process(storage: Storage, treeNodeData: TreeNodeDataAccess, sourceData: SourceDataAccess): Unit = {
		val builder = new CodeForestBuilder
		val methodCorrelationsBuilder = collection.mutable.Map.empty[String, Int]
		val dotNETAssemblyFinder = DotNet.AssemblyPairFromZip(new File(storage.name)) _
		val pathStore = new HashMap[String, Set[Option[FilePath]]] with MultiMap[String, Option[FilePath]]

		storage.readEntries(sourceFiles _) { (filename, entry, contents) =>
			val entryPath = FilePath(entry.getName)
			entryPath.foreach(ep => pathStore.addBinding(ep.name, Some(ep)))
		}

		def authoritativePath(filePath: FilePath): Option[FilePath] = {
			pathStore.get(filePath.name) match {
				case None => None
				case Some(fps) => {
					fps.flatten.find { authority => PathNormalization.isLocalizedSameAsAuthority(authority, filePath) }
				}
			}
		}

		storage.readEntries() { (filename, entry, contents) =>
			val groupName = if (filename == storage.name) group else s"Assemblies/${filename substring storage.name.length + 1}"

			if (!entry.isDirectory) {
				dotNETAssemblyFinder(entry) match {
					case Some((assembly, symbols)) => {
						val methods = new SymbolReaderHTTPServiceConnector(assembly, symbols).Methods
						for {
							(sig, size) <- methods
							filePath = FilePath(sig.file)
							authority = filePath.flatMap(authoritativePath).map(_.toString)
							treeNode <- Option(builder.getOrAddMethod(groupName, if (sig.isSurrogate) sig.surrogateFor.get else sig, size, authority))
						} methodCorrelationsBuilder += (s"${sig.containingClass}.${sig.name};${sig.modifiers};(${sig.params mkString ","});${sig.returnType}" -> treeNode.id)
					}

					case _ => // nothing
				}
			}
		}

		sourceData.importSourceFiles(builder.sourceFiles)

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
	}
}
