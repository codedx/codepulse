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

package com.secdec.codepulse.dependencycheck

import java.io.File

import com.secdec.codepulse.processing.ProcessStatusFinishedPayload

import scala.collection.JavaConversions._
import scala.util.Try

import org.owasp.dependencycheck.data.nvdcve._
import org.owasp.dependencycheck.data.update.UpdateService

sealed trait DependencyCheckStatus
sealed trait TransientDependencyCheckStatus extends DependencyCheckStatus
object DependencyCheckStatus {
	case object Queued extends TransientDependencyCheckStatus
	case object Running extends TransientDependencyCheckStatus
	case class Finished(numDeps: Int, numFlaggedDeps: Int) extends DependencyCheckStatus
	case object Failed extends DependencyCheckStatus
	case object NotRun extends DependencyCheckStatus
	case object Unknown extends DependencyCheckStatus
}

case class DependencyCheckFinishedPayload(dependencies: Int, vulnerableDependencies: Int, vulnerableNodes: Seq[Int]) extends ProcessStatusFinishedPayload

/** Wrapper for running dependency check scans.
  *
  * @author robertf
  */
object DependencyCheck {
	private def cveDbProps(implicit settings: Settings) = {
		val cveDb = new CveDB(settings.settings)
		try {
			cveDb.getDatabaseProperties
		} finally {
			cveDb.close
		}
	}

	def doUpdates()(implicit settings: Settings): Try[Unit] = Try {
		settings.withEngine { engine =>
			val svc = new UpdateService(Thread.currentThread.getContextClassLoader)
			for (src <- svc.getDataSources) {
				src.update(engine)
			}
		}
	}

	def runScan(scanSettings: ScanSettings)(implicit settings: Settings): File = {
		settings.withEngine { engine =>
			engine scan scanSettings.app
			engine.analyzeDependencies

			engine.writeReports(scanSettings.appName, scanSettings.reportDir, scanSettings.reportFormat.value)

			scanSettings.reportDir
		}
	}
}