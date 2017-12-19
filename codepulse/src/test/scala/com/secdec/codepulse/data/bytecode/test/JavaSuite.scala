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

package com.secdec.codepulse.data.bytecode.test

import java.io.File
import java.util.zip.{ ZipEntry, ZipFile }
import org.scalatest._
import org.scalatest.Matchers._
import com.secdec.codepulse.util.ZipEntryChecker
import com.secdec.codepulse.data.bytecode._


class JavaSuite extends FunSpec with Matchers {
	describe("Uploaded Java project data") {
		it("should be accepted if the archive contains Java classes compiled in Java version < 8") {
			val file = new ZipFile(getClass.getResource("java7-compiled.jar").getPath)
			val entry = file.getEntry("Main.class")
			val stream = file.getInputStream(entry)

			try {
				AsmVisitors.parseMethodsFromClass(stream)
			}
			finally {
				stream.close
			}
		}

		it("should be accepted if the archive contains Java classes compiled in Java version 8") {
			val file = new ZipFile(getClass.getResource("java8-compiled.jar").getPath)
			val entry = file.getEntry("Main.class")
			val stream = file.getInputStream(entry)

			try {
				AsmVisitors.parseMethodsFromClass(stream)
			}
			finally {
				stream.close
			}
		}

		it("should not be accepted if the archive contains Java classes compiled in Java version 9") {
			val file = new ZipFile(getClass.getResource("java9-compiled.jar").getPath)
			val entry = file.getEntry("Main.class")
			val stream = file.getInputStream(entry)

			try {
				an [IllegalArgumentException] should be thrownBy AsmVisitors.parseMethodsFromClass(stream)
			}
			finally {
				stream.close
			}
		}
	}
}