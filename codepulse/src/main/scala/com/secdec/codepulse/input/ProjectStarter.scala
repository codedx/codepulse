///*
// * Code Pulse: A real-time code coverage testing tool. For more information
// * see http://code-pulse.com
// *
// * Copyright (C) 2017 Applied Visions - http://securedecisions.avi.com
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.secdec.codepulse.input
//
//import java.io.File
//import scala.concurrent.Future
//import scala.concurrent.ExecutionContext.Implicits.global
//
//import com.secdec.codepulse.data.bytecode.{ CodeForestBuilder, CodeTreeNodeKind }
//import com.secdec.codepulse.data.model.{ ProjectData, ProjectId, TreeNodeImporter }
//import com.secdec.codepulse.dependencycheck.DependencyCheckStatus
//import com.secdec.codepulse.events.GeneralEventBus
//import com.secdec.codepulse.processing.ProcessStatus
//import com.secdec.codepulse.tracer.{ generalEventBus, projectDataProvider, projectManager }
//
//trait ProjectStarter extends Processor[ProjectData] {
//	this: BuilderFromArchive[CodeForestBuilder] =>
//
//
//	def createAndLoadProjectData(doLoad: (ProjectData, GeneralEventBus) => Unit) = {
//		val projectId = projectManager.createProject
//		val projectData = projectDataProvider getProject projectId
//
//		val futureLoad = Future {
//			doLoad(projectData, generalEventBus)
//		}
//
//		futureLoad onComplete {
//			case util.Failure(exception) =>
//				println(s"Error importing file: $exception")
//				exception.printStackTrace()
//				projectManager.removeUnloadedProject(projectId)
//
//			case util.Success(_) =>
//				for (target <- projectManager getProject projectId) {
//					target.notifyLoadingFinished()
//				}
//		}
//
//		projectData
//	}
//
//	override def process(file: File, name: String, cleanup: => Unit): ProjectData = {
//		println("~~~~~~~~~processing project")
//		createAndLoadProjectData {
//			(projectData, eventBus) => {
//				def status(processStatus: ProcessStatus): Unit = {
//					val status = processStatus match {
//						// TODO: this feels a little funky for injecting payload info
//						// TODO: this presumes certain usages that shouldn't be forced
//						case ProcessStatus.Finished(Some(payload)) => processStatus.load(Some(projectData.id, payload))
//						case _ => processStatus.load(Some(projectData.id))
//					}
//
//					eventBus.publish(status)
//				}
//
//				process(file, name, projectData.id.num.toString, cleanup, projectData.treeNodeData, status)
//
//				// The `creationDate` for project data detected in this manner
//				// should use its default value ('now'), as this is when the data
//				// was actually 'created'. The `importDate` should remain blank,
//				// since this is not an import of a .pulse file.
//				projectData.metadata.creationDate = System.currentTimeMillis
//
//				projectData
//			}
//		}
//	}
//}
