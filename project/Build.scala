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
import BuildKeys._
import Dependencies._
import com.typesafe.sbteclipse.core.EclipsePlugin._
import com.earldouglas.xsbtwebplugin._
	import PluginKeys._
	import WarPlugin._
	import WebPlugin._
	import WebappPlugin._
import Distributor.{ Keys => DistribKeys, distribSettings }
import sbtassembly.AssemblyPlugin._
import sbtassembly.AssemblyPlugin.autoImport._

object BuildDef extends Build with VersionSystem {

	lazy val liftDependencies = Seq(lift_webkit, servletApi, logback, slf4j)
	lazy val testDependencies = Seq(junit, specs, scalatest)
	lazy val libDependencies = Seq(akka, reactive, commons.io, concLinkedHashMap, juniversalchardet, dependencyCheckCore, dispatch) ++ asm ++ jackson ++ jna
	lazy val dbDependencies = Seq(slick, h2)

	val baseCompilerSettings = Seq(
		scalacOptions := List("-deprecation", "-unchecked", "-feature", "-target:jvm-1.6"),
		scalaVersion := "2.10.4"
	)

	val baseProjectSettings = net.virtualvoid.sbt.graph.Plugin.graphSettings ++ baseCompilerSettings ++ Seq(
		organization := "com.avi",
		version := "1.1.4",
		releaseDate := "1/19/2017"
	)

	val webappProjectSettings = WebPlugin.webSettings ++ Seq (
		libraryDependencies ++= Seq(jettyWebapp, jettyOrbit)
	)

	val Bytefrog = file("bytefrog")
	lazy val BytefrogAgent = ProjectRef(Bytefrog, "Agent")
	lazy val BytefrogHQ = ProjectRef(Bytefrog, "HQ")

	/* This project contains the main application's source code (Lift App).
	 */
	lazy val Core = Project("CodePulse", file("codepulse"))
		.dependsOn(BytefrogHQ)
		.settings(baseProjectSettings: _*)
		.settings(versionSettings: _*)
		.settings(webappProjectSettings: _*)
		.settings(EclipseKeys.withSource := true)
		.settings(distribSettings: _*)
		.settings(assemblySettings: _*)
		.settings(FetchCache.settings: _*)
		.settings(
			libraryDependencies ++= liftDependencies,
			libraryDependencies ++= testDependencies,
			libraryDependencies ++= libDependencies,
			libraryDependencies ++= dbDependencies
		)

	lazy val root = Project("root", file(".")).aggregate(Core)
}