/*
 * bytefrog: a tracing framework for the JVM. For more information
 * see http://code-pulse.com/bytefrog
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

package com.secdec.bytefrog.common.config.test

import org.scalatest.FunSpec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.ShouldMatchers

import com.secdec.bytefrog.common.config.StaticAgentConfiguration

class StaticAgentConfigurationSuite extends FunSpec with ShouldMatchers {
	describe("StaticAgentConfiguration parser") {
		it("should return null when parseOptionString is sent an invalid option string") {
			val result = StaticAgentConfiguration.parseOptionString("lala")
			result should be(null)
		}

		it("should return null when parseOptionString is sent a valid option string with an invalid port") {
			val result = StaticAgentConfiguration.parseOptionString("host:12z31;logfile")
			result should be(null)
		}

		it("should return a correct StaticAgentConfiguration when parseOptionString is sent a valid option string") {
			val result = StaticAgentConfiguration.parseOptionString("host:12345;mylog")
			result should have(
				'hqHost("host"),
				'hqPort(12345),
				'logFilename("mylog"))
		}
	}

	describe("StaticAgentConfiguration options") {
		val testConfig = new StaticAgentConfiguration("hostname", 7654, "logFile")

		it("should return the HQ host value when getHqHost is called") {
			val result = testConfig.getHqHost
			result should equal("hostname")
		}

		it("should return the HQ port value when getHqPort is called") {
			val result = testConfig.getHqPort
			result should equal(7654)
		}

		it("should return the log filename when getLogFilename is called") {
			val result = testConfig.getLogFilename
			result should equal("logFile")
		}
	}

	describe("StaticAgentConfiguration option string output") {
		val testConfig = new StaticAgentConfiguration("host", 1234, "logFile", 30)

		it("should return the correct option string when toOptionString is called") {
			val result = testConfig.toOptionString
			result should equal("host:1234;log=logFile;connectTimeout=30")
		}

		it("should be able to load option strings from toOptionString") {
			val optStr = testConfig.toOptionString
			val result = StaticAgentConfiguration.parseOptionString(optStr)

			result.getHqHost should equal("host")
			result.getHqPort should equal(1234)
			result.getLogFilename should equal("logFile")
			result.getConnectTimeout should equal(30)
		}
	}
}