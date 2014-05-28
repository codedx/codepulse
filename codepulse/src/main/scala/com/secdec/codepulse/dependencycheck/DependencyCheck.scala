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
import scala.collection.JavaConversions._
import scala.util.Try

import org.owasp.dependencycheck.Engine
import org.owasp.dependencycheck.data.nvdcve._
import org.owasp.dependencycheck.data.update.UpdateService
import org.owasp.dependencycheck.dependency.Dependency
import org.owasp.dependencycheck.reporting.ReportGenerator
import org.owasp.dependencycheck.utils.{ LogUtils, Settings => DepCheckSettings }

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

/** Wrapper for running dependency check scans.
  *
  * @author robertf
  */
object DependencyCheck {
	private lazy val cveDbProps = {
		val cveDb = new CveDB
		try {
			cveDb.open
			cveDb.getDatabaseProperties
		} finally {
			cveDb.close
		}
	}

	def doUpdates()(implicit settings: Settings): Try[Unit] = Try {
		DepCheckSettings.initialize
		settings.applySettings

		try {
			val svc = new UpdateService(Thread.currentThread.getContextClassLoader)
			for (src <- svc.getDataSources) {
				src.update
			}
		} finally {
			DepCheckSettings.cleanup(true)
		}
	}

	def runScan(scanSettings: ScanSettings)(implicit settings: Settings): File = {
		DepCheckSettings.initialize
		settings.applySettings

		val scanner = new Engine
		try {
			scanner scan scanSettings.app
			scanner.analyzeDependencies

			val report = new ReportGenerator(scanSettings.appName, scanner.getDependencies, scanner.getAnalyzers, cveDbProps)
			report.generateReports(scanSettings.reportDir.getCanonicalPath, scanSettings.reportFormat.value)

			scanSettings.reportDir
		} finally {
			scanner.cleanup
			DepCheckSettings.cleanup(true)
		}
	}
}