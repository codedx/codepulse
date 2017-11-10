val baseSettings = Seq(
	organization := "com.codedx",
	version := "2.0.0-SNAPSHOT",
	BuildKeys.releaseDate := "SNAPSHOT"
)

val scalaSettings = Seq(
	scalacOptions := List("-deprecation", "-unchecked", "-feature", "-target:jvm-1.7"),
	scalaVersion := "2.10.4"
)

val javaSettings = Seq(
	javacOptions := List("-source", "1.7", "-target", "1.7", "-Xlint:-options")
)

val javaOnly = Seq(
	autoScalaLibrary := false,
	crossPaths := false
)

val withTesting = Seq(
	libraryDependencies += Dependencies.scalactic,
	libraryDependencies += Dependencies.scalaTest,
	libraryDependencies += Dependencies.scalaMock
)

lazy val Bytefrog = file("bytefrog")

lazy val BytefrogFilterInjector = ProjectRef(Bytefrog, "FilterInjector")
lazy val BytefrogInstrumentation = ProjectRef(Bytefrog, "Instrumentation")
lazy val BytefrogUtil = ProjectRef(Bytefrog, "Util")

lazy val RepackagedAsm = ProjectRef(Bytefrog, "RepackagedAsm")
lazy val RepackagedMinlog = ProjectRef(Bytefrog, "RepackagedMinlog")

lazy val Shared = Project("Shared", file("shared"))
	.settings(
		baseSettings,
		scalaSettings,
		javaSettings,
		javaOnly
	)

lazy val Agent = Project("Agent", file("agent"))
	.dependsOn(
		BytefrogFilterInjector,
		BytefrogInstrumentation,
		BytefrogUtil,
		Shared,
		RepackagedAsm,
		RepackagedMinlog
	)
	.settings(
		baseSettings,
		scalaSettings,
		javaSettings,
		javaOnly,
		withTesting,

		// temporarily disable tests
		Keys.test in assembly := {},

		// put the servlet API in the provided scope; we'll deal with resolving references
		// later, and we don't want to conflict with any containers we're injecting ourselves
		// into...
		libraryDependencies += Dependencies.servletApi % "provided",

		// assembly settings for agent jar
		assemblyJarName in assembly := "agent.jar",
		packageOptions in assembly += {
			Package.ManifestAttributes(
				"Premain-Class" -> "com.codedx.codepulse.agent.javaagent.JavaAgent",
				"Agent-Class" -> "com.codedx.codepulse.agent.javaagent.JavaAgent",
				"Boot-Class-Path" -> (assemblyJarName in assembly).value,
				"Can-Redefine-Classes" -> "true",
				"Can-Retransform-Classes" -> "true"
			)
		}
	)

lazy val HQ = Project("HQ", file("hq"))
	.dependsOn(Shared)
	.settings(
		baseSettings,
		scalaSettings,
		withTesting,

		libraryDependencies += Dependencies.reactive
	)

lazy val CodePulse = Project("CodePulse", file("codepulse"))
	.dependsOn(Shared, HQ)
	.settings(
		baseSettings,
		scalaSettings,
		withTesting,

		com.earldouglas.xsbtwebplugin.WebPlugin.webSettings,

		VersionSystem.versionSettings,
		Distributor.distribSettings(Agent),
		FetchCache.settings,

		libraryDependencies ++= Seq(
			Dependencies.jettyWebapp, Dependencies.jettyOrbit, Dependencies.servletApi,
			Dependencies.lift_webkit, Dependencies.logback, Dependencies.slf4j,
			Dependencies.akka, Dependencies.reactive, Dependencies.commons.io, Dependencies.concLinkedHashMap, Dependencies.juniversalchardet, Dependencies.dependencyCheckCore,
			Dependencies.slick, Dependencies.h2
		) ++ Dependencies.asm ++ Dependencies.jackson ++ Dependencies.jna
	)