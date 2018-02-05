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
//
//import com.secdec.codepulse.data.bytecode.CodeForestBuilder
//import com.secdec.codepulse.data.model.{ ProjectData, TreeNodeDataAccess }
//import com.secdec.codepulse.dependencycheck.DependencyCheckStatus
//import com.secdec.codepulse.events.GeneralEventBus
//import com.secdec.codepulse.processing.ProcessStatus
//
//trait BuilderFromArchive[T] {
////	val file: File
////	val name: String
////	val cleanup: () => Unit
////	val projectData: ProjectData
//
//	def passesQuickCheck(file: File): Boolean
//	def process(file: File, name: String, identifier: String, cleanup: => Unit, treeNodeData: TreeNodeDataAccess, status: ProcessStatus => Unit): T
//	def fail(action: String, exception: Exception)
//
////	def process(file: File, name: String, cleanup: => Unit): ProjectData = {
////		process(file, name, cleanup, projectData.treeNodeData)
////	}
//}
//
//trait LanguageBuilder[T] extends BuilderFromArchive[T] {
//	def eventBus: GeneralEventBus
//	override def fail(action: String, exception: Exception) = {
//
//	}
//}
//
//// eventually want a mixin as well
//// trait TheMixin { self: ZipArchiveHandler =>
//// override def process(): = { super.process(); mixinProcess() }
//
//// The goal is to mixin the dependency check capability as needed.
//// The ZipArchiveHandler may or may not be the appropriate super type, but this captures the intent
//// Process the archive first, get the different outputs and data objects generated, and feed them into the dep check process
//
//// val processor = new ProjectStarter with BuilderFromDotNETArchive with DependencyCheck
//// val processor = new BuilderFromDotNETArchive with DependencyCheck with ProjectStarter
////
//// processor.process()