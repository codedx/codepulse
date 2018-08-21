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
import scala.collection.mutable.{ HashMap, MultiMap, Set }

import com.secdec.codepulse.data.bytecode.{ CodeForestBuilder, CodeTreeNodeKind }
import com.secdec.codepulse.data.dotnet.{ DotNet, SymbolReaderHTTPServiceConnector, SymbolService }
import com.secdec.codepulse.data.model.{ MethodSignatureNode, SourceDataAccess, TreeNodeDataAccess, TreeNodeImporter }
import com.secdec.codepulse.data.storage.Storage
import com.secdec.codepulse.input.LanguageProcessor
import com.secdec.codepulse.input.pathnormalization.{ FilePath, NestedPath, PathNormalization }
import scala.language.postfixOps
import org.apache.commons.io.FilenameUtils

class DotNETProcessor() extends LanguageProcessor {
	val group = "Classes"
	val traceGroups = (group :: Nil).toSet
	val sourceExtensions = List("cs", "vb", "fs", "fsi", "fsx", "fsscript", "cpp")

	val symbolService = new SymbolService
	symbolService.create

	def canProcess(storage: Storage): Boolean = {
		storage.find() { (_, _, entry, _) =>
			val extension = FilenameUtils.getExtension(entry.getName)
			!entry.isDirectory && (extension == "exe" || extension == "dll")
		}
	}

	def process(storage: Storage, treeNodeData: TreeNodeDataAccess, sourceData: SourceDataAccess): Unit = {
		val builder = new CodeForestBuilder
		val methodCorrelationsBuilder = collection.mutable.Map.empty[String, Int]
		val dotNETAssemblyFinder = DotNet.AssemblyPairFromZip(new File(storage.name)) _

		val pathStore = new HashMap[(String, String), Set[Option[NestedPath]]] with MultiMap[(String, String), Option[NestedPath]]

		storage.readEntries(sourceFiles _) { (filename, entryPath, entry, contents) =>
			val entryFilePath = FilePath(entry.getName)
			val groupName = if (filename == storage.name) group else s"Assemblies/${filename substring storage.name.length + 1}"
			entryFilePath.foreach(efp => {
				entryPath match {
					case Some(ep) => pathStore.addBinding((groupName, efp.name), entryPath)
					case None => pathStore.addBinding((groupName, efp.name), Some(new NestedPath(List(efp))))
				}
			})
		}

		def authoritativePath(group: String, filePath: NestedPath): Option[NestedPath] = {
			pathStore.get((group, filePath.paths.last.name)) match {
				case None => None
				case Some(fps) => {
					fps.flatten.find { authority => PathNormalization.isLocalisedSameAsAuthority(authority, filePath) }
				}
			}
		}

		storage.readEntries() { (filename, entryPath, entry, contents) =>
			val groupName = if (filename == storage.name) group else s"Assemblies/${filename substring storage.name.length + 1}"

			if (!entry.isDirectory) {
				dotNETAssemblyFinder(entry) match {
					case Some((assembly, symbols)) => {
						val methods = new SymbolReaderHTTPServiceConnector(assembly, symbols).Methods

						// process surrogate methods first to establish source file for the method they support
						val methodsSorted = methods sortWith ((left,right) => {
							if ((left._1.isSurrogate && right._1.isSurrogate) ||
								(!left._1.isSurrogate && !right._1.isSurrogate))
							{
								left._1.name > right._1.name
							}
							left._1.isSurrogate
						})

						for {
							(sig, size, sourceLocationCount, methodStartLine) <- methodsSorted
							filePath = FilePath(sig.file)
							nestedPath = filePath.map(fp => entryPath match {
								case Some(ep) => new NestedPath(ep.paths :+ fp)
								case None => new NestedPath(List(fp))
							})
							authority = nestedPath match {
								case Some(np) => authoritativePath(groupName, np).map (_.toString)
								case None => None
							}
							treeNode <- Option(builder.getOrAddMethod(groupName, if (sig.isSurrogate) sig.surrogateFor.get else sig, size, authority, Option(sourceLocationCount), Option(methodStartLine), None))
						} methodCorrelationsBuilder += (s"${sig.containingClass}.${sig.name};${sig.modifiers};(${sig.params mkString ","});${sig.returnType}" -> treeNode.id)
					}

					case _ => // nothing
				}
			}
		}

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
						case CodeTreeNodeKind.Grp | CodeTreeNodeKind.Pkg => Some(traceGroups contains root.name)
						case _ => None
					})
			}

			importer.flush

			treeNodeData.mapMethodSignatures(methodCorrelations.map { case (signature, nodeId) => MethodSignatureNode(0, signature, nodeId) })
		}
	}
}
