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

import sbt._
import Keys._

object Dependencies {
	lazy val lift_webkit = "net.liftweb" %% "lift-webkit" % "2.5" % "compile->default"
	lazy val jettyWebapp = "org.eclipse.jetty" % "jetty-webapp" % "8.1.7.v20120910" % "container"
	lazy val jettyOrbit = "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container" artifacts Artifact("javax.servlet", "jar", "jar")
	lazy val servletApi = "javax.servlet" % "javax.servlet-api"	% "3.1.0"

	lazy val logging = Seq(
		"ch.qos.logback" % "logback-classic" % "0.9.26" % "compile->default",
		"org.slf4j" % "slf4j-api" % "1.6.4",
		"org.slf4j" % "jcl-over-slf4j" % "1.6.4",
		"org.codehaus.groovy" % "groovy-all" % "2.4.13"
	)

	// for testing
	lazy val scalactic = "org.scalactic" %% "scalactic" % "3.0.4" % Test
	lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4" % Test
	lazy val scalaMock = "org.scalamock" %% "scalamock" % "4.0.0" % Test

	// extra libraries
	lazy val akka = "com.typesafe.akka" %% "akka-actor" % "2.2.3"
	lazy val reactive = "cc.co.scala-reactive" %% "reactive-core"	% "0.3.2.1"
	lazy val concLinkedHashMap = "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.2_jdk5"
	lazy val minlog = "com.esotericsoftware" % "minlog" % "1.3.0"
	lazy val asm = Seq(
		"org.ow2.asm" % "asm" % "6.0",
		"org.ow2.asm" % "asm-commons" % "6.0"
	)
	lazy val jsonb = Seq(
		"javax.json" % "javax.json-api" % "1.1",
		"javax.json.bind" % "javax.json.bind-api" % "1.0",
		"org.glassfish" % "javax.json" % "1.1",
		"org.eclipse" % "yasson" % "1.0"
	)
	lazy val jna = Seq(
		"net.java.dev.jna" % "jna" % "4.1.0",
		"net.java.dev.jna" % "jna-platform" % "4.1.0"
	)
	lazy val jackson = Seq(
		"com.fasterxml.jackson.core" % "jackson-core" % "2.3.2",
		"com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.3.2"
	)
	lazy val astamCorrelator = Seq(
		"org.jsoup" % "jsoup" % "1.11.3",
		"com.github.secdec.astam-correlator" % "master-pom" % "1.3.3",
		"com.github.secdec.astam-correlator" % "threadfix-entities" % "1.3.3",
		"com.github.secdec.astam-correlator" % "threadfix-ham" % "1.3.3"
	)
	lazy val juniversalchardet = "com.googlecode.juniversalchardet" % "juniversalchardet" % "1.0.3"

	// database related
	lazy val slick = "com.typesafe.slick" %% "slick" % "2.0.1"
	lazy val h2 = "com.h2database" % "h2" % "1.4.197"

	// apache commons dependencies
	object commons {
		lazy val io = "commons-io" % "commons-io" % "2.1"
		lazy val lang = "org.apache.commons" % "commons-lang3" % "3.7"
	}

	// dependency-check
	lazy val dependencyCheckCore = "org.owasp" % "dependency-check-core" % "3.1.2"

	// dispatch, Scala wrapper for Java AsyncHttpClient https://github.com/dispatch/reboot
	lazy val dispatch = "net.databinder.dispatch" %% "dispatch-core" % "0.11.4"

	lazy val javaparser = "com.github.javaparser" % "javaparser-core" % "2.4.0"
}
