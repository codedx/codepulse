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

import com.secdec.codepulse.data.bytecode.CodeForestBuilder
import com.secdec.codepulse.data.model.{ ProjectData, TreeNodeFlag }
import com.secdec.codepulse.dependencycheck
import com.secdec.codepulse.components.dependencycheck.{ Updates => DependencyCheckUpdates }
import com.secdec.codepulse.dependencycheck.{ DependencyCheckActor, DependencyCheckStatus, ScanSettings }
import com.secdec.codepulse.input.{ BuilderFromArchive, Processor }

trait DependencyCheck extends Processor[CodeForestBuilder] {
	this: BuilderFromArchive[CodeForestBuilder] =>

	abstract override def process(projectData: ProjectData): CodeForestBuilder = {
		val codeForest = super.process(projectData)
		doDependencyCheck(projectData)

		codeForest
	}

	def updateStatus(projectData: ProjectData, status: DependencyCheckStatus, vulnNodes: Seq[Int] = Nil) {
		projectData.metadata.dependencyCheckStatus = status
		DependencyCheckUpdates.pushUpdate(projectData.id, projectData.metadata.dependencyCheckStatus, vulnNodes)
	}

	def doDependencyCheck(projectData: ProjectData): Unit = {
		println("~~~~~~~~~processing dependency check")
		updateStatus(projectData, DependencyCheckStatus.Queued)

		val treeNodeData = projectData.treeNodeData
		val scanSettings = ScanSettings(file, name, projectData.id)

		dependencycheck.dependencyCheckActor() ! DependencyCheckActor.Run(scanSettings) {
			// before running, set status to running
			updateStatus(projectData, DependencyCheckStatus.Running)
		} { reportDir =>
			try {
				// on successful run, process the results
				import scala.xml._
				import com.secdec.codepulse.util.RichFile._
				import treeNodeData.ExtendedTreeNodeData

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
						val jarLabel = f.pathSegments.drop(file.pathSegments.length).mkString("JARs / ", " / ", "")
						for (node <- treeNodeData getNode jarLabel) {
							node.flags += TreeNodeFlag.HasVulnerability
							vulnNodes += node.id
						}
					}
				}

				updateStatus(projectData, DependencyCheckStatus.Finished(deps, vulnDeps), vulnNodes.result)
			} finally {
				cleanup
			}
		} { exception =>
			try {
				// on error, set status to failed
				println(s"Dependency Check for project ${projectData.id} failed to run: $exception")
				updateStatus(projectData, DependencyCheckStatus.Failed)
			} finally {
				cleanup
			}
		}
	}
}
