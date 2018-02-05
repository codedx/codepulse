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

package com.secdec.codepulse.input.dependencycheck

import java.io.File

import akka.actor.{ Actor, Stash }
import com.secdec.codepulse.data.model.{ TreeNodeDataAccess, TreeNodeFlag }
import com.secdec.codepulse.dependencycheck
import com.secdec.codepulse.dependencycheck.{ DependencyCheck, DependencyCheckActor, ScanSettings, Settings }
import com.secdec.codepulse.events.GeneralEventBus
import com.secdec.codepulse.input.LanguageProcessor
import com.secdec.codepulse.processing.{ ProcessEnvelope, ProcessStatus }
import com.secdec.codepulse.processing.ProcessStatus.{ DataInputAvailable, PostProcessDataAvailable, ProcessDataAvailable }
import org.owasp.dependencycheck.utils.{ Settings => DepCheckSettings }

class DependencyCheckPostProcessor(eventBus: GeneralEventBus, scanSettings: (String, File) => ScanSettings) extends Actor with Stash {
	// TODO: "can process" capability for post-process tools
	def receive = {
		case ProcessEnvelope(_, ProcessDataAvailable(identifier, file, treeNodeData)) => {
			def status(processStatus: ProcessStatus): Unit = {
				eventBus.publish(processStatus)
			}

			try {
//				eventBus.publish(ProcessStatus.Running(identifier))
				process(identifier, scanSettings(identifier, file), treeNodeData, status)
				eventBus.publish(PostProcessDataAvailable(identifier, None))
			} catch {
				case exception: Exception => eventBus.publish(ProcessStatus.Failed(identifier, "Dependency Check", Some(exception)))
			} finally {
				file.delete()
			}
		}
	}

	def process(identifier: String, scanSettings: ScanSettings, treeNodeData: TreeNodeDataAccess, status: ProcessStatus => Unit): Unit = {
		//		updateStatus(projectData, DependencyCheckStatus.Queued)
		status(ProcessStatus.Queued(identifier))

		//val scanSettings = ScanSettings(file, name, identifier)

//		dependencycheck.dependencyCheckActor() ! DependencyCheckActor.Run(scanSettings) {
//			// before running, set status to running
//			//			updateStatus(projectData, DependencyCheckStatus.Running)
			status(ProcessStatus.Running(identifier))
//		} { reportDir =>
				// on successful run, process the results
		try {
			import scala.xml._
			import com.secdec.codepulse.util.RichFile._
			import treeNodeData.ExtendedTreeNodeData

			val reportDir = DependencyCheck.runScan(scanSettings)
			val x = XML loadFile reportDir / "dependency-check-report.xml"

			var deps = 0
			var vulnDeps = 0
			val vulnNodes = List.newBuilder[Int]

			for {
				dep <- x \\ "dependency"
				vulns = dep \\ "vulnerability"
			} {
				deps += 1
				if (!vulns.isEmpty) {
					vulnDeps += 1
					val f = new File((dep \ "filePath").text)
					val jarLabel = f.pathSegments.drop(scanSettings.app.pathSegments.length).mkString("JARs / ", " / ", "")
					for (node <- treeNodeData getNode jarLabel) {
						node.flags += TreeNodeFlag.HasVulnerability
						vulnNodes += node.id
					}
				}
			}

			//				updateStatus(projectData, DependencyCheckStatus.Finished(deps, vulnDeps), vulnNodes.result)
			status(ProcessStatus.Finished(identifier, Some((deps, vulnDeps, vulnNodes.result))))
		} catch {
			case exception: Exception => status(ProcessStatus.Failed(identifier, "Dependency Check", Some(exception)))
				// on error, set status to failed
				//				println(s"Dependency Check for project ${projectData.id} failed to run: $exception")
				//				updateStatus(projectData, DependencyCheckStatus.Failed)
		}
	}
}
