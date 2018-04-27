/*
 * Copyright 2017 Secure Decisions, a division of Applied Visions, Inc.
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
 *
 * This material is based on research sponsored by the Department of Homeland
 * Security (DHS) Science and Technology Directorate, Cyber Security Division
 * (DHS S&T/CSD) via contract number HHSP233201600058C.
 */
package com.secdec.codepulse.data.storage

import java.io.File
import java.util.zip.ZipFile

import org.apache.commons.io.FileUtils

import com.secdec.codepulse.data.model.{ ProjectDataProvider, ProjectId }
import com.secdec.codepulse.util.Implicits._
import com.secdec.codepulse.tracer.projectDataProvider

trait InputStore {
	def storeInput(projectId: ProjectId, file: File): Option[Storage]
}

trait InputRetrieve {
	def getStorageFor(projectId: ProjectId): Option[Storage]
}

object StorageManager extends InputStore with InputRetrieve {
	def storeInput(projectId: ProjectId, file: File): Option[Storage] = {
		// Copy file to storage location
		val projectDir = getProjectDirectoryFor(projectId)
		val destinationFile = projectDir / file.getName
		copyFileTo(file, destinationFile)

		// Set file knowledge into project database
		setProjectInput(projectId, destinationFile)

		// Return a storage object that operates on file as the root
		//      Assumes a zip archive
		val zipFile = new ZipFile(destinationFile.getCanonicalPath)
		Option(new ZippedStorage(zipFile))
	}

	def getStorageFor(projectId: ProjectId): Option[Storage] = {
		// Retrieve input file knowledge from project database
		val input = getProjectInput(projectId)

		// Look in storage location for file
		val inputFile = new File(input)
		if(inputFile.exists && inputFile.canRead){
			// Load file as storage objects
			val zipFile = new ZipFile(inputFile)
			// Return storage object
			Option(new ZippedStorage(zipFile))
		} else {
			None
		}
	}

	private def getProjectDirectoryFor(id: ProjectId): File = {
		val storageRoot = ProjectDataProvider.DefaultStorageDir
		val projectDirectoryName = s"project-${id.num}"
		storageRoot / projectDirectoryName
	}

	private def copyFileTo(file: File, destination: File) = {
		FileUtils.copyFile(file, destination)
	}

	private def setProjectInput(projectId: ProjectId, file: File) = {
		projectDataProvider.getProject(projectId).metadata.input = file.getCanonicalPath
	}

	private def getProjectInput(projectId: ProjectId): String = {
		projectDataProvider.getProject(projectId).metadata.input
	}
}