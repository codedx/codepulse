/*
 * Code Pulse: A real-time code coverage testing tool. For more information
 * see http://code-pulse.com
 *
 * Copyright (C) 2014 Applied Visions - http://securedecisions.avi.com
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

package com.secdec.codepulse.tracer

import java.io.File

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.apache.commons.io.FilenameUtils

import com.secdec.codepulse.data.bytecode.AsmVisitors
import com.secdec.codepulse.data.bytecode.CodeForestBuilder
import com.secdec.codepulse.data.bytecode.CodeTreeNodeKind
import com.secdec.codepulse.data.jsp.JasperJspAdapter
import com.secdec.codepulse.data.jsp.JspAnalyzer
import com.secdec.codepulse.data.trace.TraceData
import com.secdec.codepulse.data.trace.TraceId
import com.secdec.codepulse.data.trace.TreeNodeImporter
import com.secdec.codepulse.tracer.export.TraceImporter
import com.secdec.codepulse.util.SmartLoader
import com.secdec.codepulse.util.ZipEntryChecker

import net.liftweb.common.Box
import net.liftweb.common.Failure
import net.liftweb.common.Full

object TraceUploadData {

	def handleTraceExport(file: File): TraceId = createAndLoadTraceData { traceData =>
		TraceImporter.importFrom(file, traceData)

		// Note: the `creationDate` should have been filled in by the importer.
		//The `importDate` is now.
		traceData.metadata.importDate = Some(System.currentTimeMillis)
	}

	/** A naive check on a File that checks if it is a .zip file
	  * that contains at least one .class file. This will identify
	  * .jar and .war files as well, since they are zip files internally.
	  */
	def checkForBinaryZip(file: File): Boolean = {
		ZipEntryChecker.checkForZipEntries(file) { entry =>
			!entry.isDirectory && entry.getName.endsWith(".class")
		}
	}

	/** A preliminary check on a File to see if it looks like an
	  * exported .pulse file.
	  */
	def checkForTraceExport(file: File): Boolean = {
		TraceImporter.canImportFrom(file)
	}

	/** Immediately adds a new Trace to the traceManager, and returns its id.
	  * Meanwhile, it starts a task that will call `doLoad` on the id and the
	  * newly-initialized TraceData instance. When the `doLoad` task completes,
	  * if it was a failure, the trace will be removed from the manager. If
	  * the loading process finished successfully, the associated TracingTarget
	  * will be notified so that it can leave the 'loading' state.
	  *
	  * The goal of this is to allow users to immediately navigate to the new
	  * trace page when they upload a file, without having to wait for the actual
	  * (heavy-duty) file processing logic. Simple checks should be performed on
	  * any file before processing it in this way, to avoid a useless redirect.
	  *
	  * An example:
	  * If a user uploads a huge .war file, processing may take around a minute.
	  * But since the processing is done elsewhere, the user can see that the
	  * upload itself was successful; they will see a 'loading' screen on the
	  * trace page, rather than waiting for a progress bar in the upload form.
	  */
	def createAndLoadTraceData(doLoad: TraceData => Unit) = {
		val traceId = traceManager.createTrace
		val traceData = traceDataProvider getTrace traceId

		val futureLoad = Future {
			doLoad(traceData)
		}

		futureLoad onComplete {
			case util.Failure(exception) =>
				println(s"Error importing file: $exception")
				exception.printStackTrace()
				traceManager.removeUnloadedTrace(traceId)

			case util.Success(_) =>
				for (target <- traceManager getTrace traceId) {
					target.notifyLoadingFinished()
				}
		}

		traceId
	}

	def handleBinaryZip(file: File): TraceId = createAndLoadTraceData { traceData =>
		val RootGroupName = "Classes"
		val tracedGroups = (RootGroupName :: CodeForestBuilder.JSPGroupName :: Nil).toSet
		val builder = new CodeForestBuilder
		val methodCorrelationsBuilder = collection.mutable.Map.empty[String, Int]

		//TODO: make this configurable somehow
		val jspAdapter = new JasperJspAdapter

		val loader = new SmartLoader

		ZipEntryChecker.forEachEntry(file) { (filename, entry, contents) =>
			val groupName = if (filename == file.getName) RootGroupName else s"JARs/$filename"
			if (!entry.isDirectory) {
				FilenameUtils.getExtension(entry.getName) match {
					case "class" =>
						val methods = AsmVisitors.parseMethodsFromClass(contents)
						for {
							(name, size) <- methods
							treeNode <- builder.getOrAddMethod(groupName, name, size)
						} methodCorrelationsBuilder += (name -> treeNode.id)

					case "jsp" =>
						val jspContents = loader loadStream contents
						val jspSize = JspAnalyzer analyze jspContents
						jspAdapter.addJsp(entry.getName, jspSize)

					case _ => // nothing
				}
			} else if (entry.getName.endsWith("WEB-INF/")) {
				jspAdapter addWebinf entry.getName
			}
		}

		val jspCorrelations = jspAdapter build builder
		val treemapNodes = builder.condensePathNodes().result
		val methodCorrelations = methodCorrelationsBuilder.result

		if (treemapNodes.isEmpty) {
			throw new NoSuchElementException("No method data found in analyzed upload file.")
		} else {
			val importer = TreeNodeImporter(traceData.treeNodeData)

			importer ++= treemapNodes.toIterable map {
				case (root, node) =>
					node -> (node.kind match {
						case CodeTreeNodeKind.Grp | CodeTreeNodeKind.Pkg => Some(tracedGroups contains root.name)
						case _ => None
					})
			}

			importer.flush

			traceData.treeNodeData.mapJsps(jspCorrelations)
			traceData.treeNodeData.mapMethodSignatures(methodCorrelations)

			// The `creationDate` for trace data detected in this manner
			// should use its default value ('now'), as this is when the data
			// was actually 'created'. The `importDate` should remain blank,
			// since this is not an import of a .pulse file.
			traceData.metadata.creationDate = System.currentTimeMillis
		}
	}

	def handleUpload(file: File): Box[TraceId] = {
		if (checkForTraceExport(file)) {
			Full(handleTraceExport(file))
		} else if (checkForBinaryZip(file)) {
			Full(handleBinaryZip(file))
		} else {
			Failure("Invalid upload file")
		}
	}

}

