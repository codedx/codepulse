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

package com.secdec.codepulse.data.dotnet

import java.io.{ File, FileOutputStream }
import java.util.zip.{ ZipEntry, ZipFile }
import scala.util.{ Failure, Success, Try }

import com.secdec.codepulse.data.MethodSignature
import org.apache.commons.io.{ FileUtils, FilenameUtils }

trait DotNetBuilder {
	def Methods: List[(MethodSignature, Int)]
}

case class MethodInfo(
	id: String,
	fullyQualifiedName: String,
	containingClass: String,
	file: String,
	accessModifiers: Int,
	parameters: List[String],
	returnType: String,
	instructions: Int,
	surrogateFor: String
)

object DotNet {
	val SYMBOL_EXTENSIONS = "pdb" :: "mdb" :: Nil

	def AssemblyPairFromZip(zipFile: File)(zipEntry: ZipEntry): Option[(File, File)] = {
		Try { new ZipFile(zipFile) } match {
			case Failure(_) => None

			case Success(zipFile) => {
				val entryExtension = FilenameUtils.getExtension(zipEntry.getName)
				entryExtension match {
					case "exe" | "dll" => {
						// Is the entry an assembly type (exe, dll)?
						// If so, do we have a matching symbol type (pdb, mdb)?
						// If so, create files for them that can be analyzed

						val entryPart = zipEntry.getName.replaceAll("\\.[^.]*$", "")
						val potentialSymbolFiles = SYMBOL_EXTENSIONS.map(extension => s"$entryPart.$extension")
						val existingSymbolFiles = potentialSymbolFiles.flatMap(path => Option(zipFile.getEntry(path)))

						(for {
							symbolFile <- existingSymbolFiles.headOption
						} yield {
							val assembly = File.createTempFile("assembly", "tmp")
							assembly.deleteOnExit()
							FileUtils.copyInputStreamToFile(zipFile.getInputStream(zipEntry), assembly)

							val symbols = File.createTempFile("symbols", "tmp")
							symbols.deleteOnExit()
							FileUtils.copyInputStreamToFile(zipFile.getInputStream(symbolFile), symbols)

							Some(assembly, symbols)
						}) getOrElse None
					}

					case _ => None
 				}
			}
		}
	}
}