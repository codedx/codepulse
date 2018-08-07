val shared = Seq(
	organization := "com.codedx",
	scalacOptions := Seq("-deprecation", "-unchecked", "-feature"),
	scalaVersion := "2.12.4",
	javacOptions := Seq("-source", "1.7", "-target", "1.7", "-Xlint:-options")
)

val javaOnly = Seq(
	autoScalaLibrary := false,
	crossPaths := false
)

val javaWarnings = Seq(
	javacOptions ++= Seq("-Xlint:unchecked")
)

val withTesting = Seq(
	libraryDependencies += Dependencies.scalactic,
	libraryDependencies += Dependencies.scalaTest,
	libraryDependencies += Dependencies.scalaMock
)

lazy val Instrumentation = (project in file("instrumentation"))
	.dependsOn(SourceMapParser)
	.settings(
		shared,
		javaOnly,
		javaWarnings,
		withTesting,

		libraryDependencies ++= Dependencies.asm,
		libraryDependencies += Dependencies.minlog,
		libraryDependencies += Dependencies.logback,
		libraryDependencies += Dependencies.slf4j
	)

lazy val FilterInjector = (project in file("filter-injector"))
	.dependsOn(Util)
	.settings(
		shared,
		javaOnly,
		javaWarnings,

		libraryDependencies ++= Dependencies.asm,
		libraryDependencies += Dependencies.minlog,
		libraryDependencies += Dependencies.logback,
		libraryDependencies += Dependencies.slf4j
	)

lazy val SourceMapParser = (project in file("sourcemap-parser"))
	.settings(
		shared,
		javaOnly
	)

lazy val Util = (project in file("util"))
	.settings(
		shared,
		javaOnly,
		javaWarnings,
		withTesting,

		libraryDependencies ++= Dependencies.asm,
		libraryDependencies += Dependencies.minlog,
		libraryDependencies += Dependencies.logback,
		libraryDependencies += Dependencies.slf4j
	)

lazy val Stack = (project in file("."))
	.aggregate(Instrumentation, FilterInjector, SourceMapParser, Util)