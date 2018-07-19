val baseSettings = Seq(
	organization := "com.codedx",
	version := "UNVERSIONED",
	BuildKeys.releaseDate := "N/A"
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

lazy val Shared = Project("Shared", file("shared"))
	.settings(
		baseSettings,
		scalaSettings,
		javaSettings,
		withTesting,

		libraryDependencies ++= Dependencies.jsonb ++ Dependencies.logging
	)

lazy val Agent = Project("Agent", file("agent"))
	.enablePlugins(AssemblyPlugin)
	.dependsOn(
		BytefrogFilterInjector,
		BytefrogInstrumentation,
		BytefrogUtil,
		Shared
	)
	.settings(
		baseSettings,
		scalaSettings,
		javaSettings,
		javaOnly,
		withTesting,

		// put the servlet API in the provided scope; we'll deal with resolving references
		// later, and we don't want to conflict with any containers we're injecting ourselves
		// into...
		libraryDependencies ++= Seq(
			Dependencies.servletApi % "provided"
		) ++ Dependencies.jsonb ++ Dependencies.asm,

		libraryDependencies += Dependencies.minlog,

		assembledMappings in assembly := {
			val mappings = (assembledMappings in assembly).value
			def fileStartsWith(file: String, prefix: String) = {
				val frontslashFile = file.replaceAllLiterally("\\", "/")
				frontslashFile.startsWith(prefix)
			}
			val warnItems = for {
				set <- mappings
				(f, dest) <- set.mappings
				if f.isFile
				if
					dest != "module-info.class" && // we exclude this in the merge strategy
					!fileStartsWith(dest, "META-INF/") &&
					!fileStartsWith(dest, "beans_1_0.xsd") &&
					!fileStartsWith(dest, "beans_1_1.xsd") &&
					!fileStartsWith(dest, "beans_2_0.xsd") &&
					!fileStartsWith(dest, "messages.properties") &&
					!fileStartsWith(dest, "library.properties") &&
					!fileStartsWith(dest, "overview.html") &&
					!fileStartsWith(dest, "overviewj.html") &&
					!fileStartsWith(dest, "rootdoc.txt") &&
					!fileStartsWith(dest, "com/codedx/bytefrog/") &&
					!fileStartsWith(dest, "com/codedx/codepulse/agent/") &&
					!fileStartsWith(dest, "com/codedx/codepulse/utility/")
			} yield dest

			if (warnItems.nonEmpty) sys.error(s"Items outside of our namespace (do they need to be shaded?): ${warnItems mkString ", "}")

			mappings
		},

		assemblyShadeRules in assembly := Seq(
			ShadeRule.rename("ch.qos.logback.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("com.esotericsoftware.minlog.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("fm.ua.ikysil.smap.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("groovy.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("groovyjarjarantlr.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("groovyjarjarasm.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("groovyjarjarcommonscli.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("javax.decorator.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("javax.el.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("javax.enterprise.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("javax.inject.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("javax.interceptor.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("javax.json.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("org.apache.commons.logging.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("org.apache.groovy.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("org.codehaus.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("org.eclipse.yasson.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("org.glassfish.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("org.objectweb.asm.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("org.slf4j.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll,
			ShadeRule.rename("scala.**" -> "com.codedx.codepulse.agent.thirdparty.@0").inAll
		),

		assemblyMergeStrategy in assembly := {
			case "module-info.class" => MergeStrategy.discard
			case x =>
				val oldStrategy = (assemblyMergeStrategy in assembly).value
				oldStrategy(x)
		},

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

		libraryDependencies += Dependencies.commons.lang,
		libraryDependencies ++= Seq(Dependencies.reactive, Dependencies.dispatch) ++ Dependencies.jsonb ++ Dependencies.logging
	)

lazy val CodePulse = Project("CodePulse", file("codepulse"))
	.dependsOn(Shared, HQ)
	.settings(
		compile in Compile := (Def.taskDyn {
      			val c = (compile in Compile).value
      			Def.task {
				import sys.process._
                
                var powershellCmd = "powershell"
                if (System.getProperty("os.name") == "Linux") {
	                powershellCmd = "pwsh"
                }

                val publishCmd = powershellCmd + " -file publish-symbol-service.ps1"
                println("Running: " + publishCmd)

				val exitCode = publishCmd.!

				val msg = if (exitCode == 0) "The .NET Symbol Service is up-to-date" else "ERROR: UNABLE TO PUBLISH .NET SYMBOL SERVICE!!!"
				println(msg)
				c
      			}
    		}).value,
		baseSettings,
		scalaSettings,
		withTesting,

		com.earldouglas.xsbtwebplugin.WebPlugin.webSettings,

		VersionSystem.versionSettings,
		Distributor.distribSettings(Agent),
		FetchCache.settings,

		libraryDependencies ++= Seq(
			Dependencies.jettyWebapp, Dependencies.jettyOrbit, Dependencies.servletApi,
			Dependencies.lift_webkit,
			Dependencies.akka, Dependencies.reactive,
			Dependencies.commons.io, Dependencies.commons.lang,
			Dependencies.concLinkedHashMap, Dependencies.juniversalchardet, Dependencies.dependencyCheckCore,
			Dependencies.slick, Dependencies.h2,
			Dependencies.javaparser
		) ++ Dependencies.asm ++ Dependencies.jackson ++ Dependencies.jna ++ Dependencies.logging
	)