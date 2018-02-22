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

import java.io.File
import java.nio.file.Paths
import scala.sys.process._

import com.typesafe.config.ConfigFactory
import net.liftweb.common.Loggable

import bootstrap.liftweb.AppCleanup

trait Lifetime {
	def create: Unit
	def destroy: Unit
}

class SymbolService extends Lifetime with Loggable {
	var process: Option[Process] = None

	override def create: Unit = {
		logger.debug("attempt to create SymbolService")
		try {
			val config = ConfigFactory.load()
			val symbolServiceBinary = config.getString("cp.symbol-service.binary")
			val symbolServiceLocation = config.getString("cp.symbol-service.location")
			val binaryPath = Paths.get(symbolServiceLocation, symbolServiceBinary).toString
			val port = config.getString("cp.symbol-service.port")
			var url = s"http://*:$port"

			var binaryCanonical = new File(binaryPath).getCanonicalPath
			logger.debug(s"attempting to start $binaryCanonical using $url...")
			process = Some(Process(s"$binaryPath", new java.io.File(symbolServiceLocation), "ASPNETCORE_URLS" -> url).run())
			logger.debug(s"created SymbolService from $binaryPath")

			def shutdown(area: String): () => Unit = {
				() => {
					logger.debug(s"$area shutdown hook attempting to destroy process")
					try {
						destroy
					} catch {
						case e: Exception => {
							logger.error("failed to destroy process")
							logger.error(s"$e")
						}
					}
				}
			}

			AppCleanup addPreShutdownHook shutdown("Code Pulse")
			sys addShutdownHook shutdown("JVM")
		} catch {
			case e: Exception => {
				logger.error("could not create SymbolService due to an exception}")
				logger.error(s"$e")
			}
		}
	}

	override def destroy: Unit = {
		logger.debug(s"attempt to destroy process")
		process.foreach(_.destroy())
		process.foreach(p => logger.debug(s"process exited with exit value ${p.exitValue}"))
		process = None
	}
}