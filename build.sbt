val shared = Seq(
	organization := "com.codedx",
	scalacOptions := List("-deprecation", "-unchecked", "-feature"),
	scalaVersion := "2.12.4",
	javacOptions := List("-source", "1.7", "-target", "1.7", "-Xlint:-options", "-Xlint:unchecked")
)

val javaOnly = Seq(
	autoScalaLibrary := false,
	crossPaths := false
	// unmanagedSourceDirectories in Compile <<= (javaSource in Compile) { _ :: Nil }
)

val withTesting = Seq(
	libraryDependencies += Dependencies.scalactic,
	libraryDependencies += Dependencies.scalaTest,
	libraryDependencies += Dependencies.scalaMock
)

lazy val RepackagedAsm = project
	.settings(
		shared,
		javaOnly,
		Repackager("asm", Dependencies.asm, Repackager.Rename("org.objectweb.asm.**", "com.codedx.bytefrog.thirdparty.asm.@1")).settings
	)

lazy val RepackagedMinlog = project
	.settings(
		shared,
		javaOnly,
		Repackager("minlog", Dependencies.minlog, Repackager.Rename("com.esotericsoftware.minlog.**", "com.codedx.bytefrog.thirdparty.minlog.@1")).settings
	)

lazy val Instrumentation = (project in file("instrumentation"))
	.dependsOn(RepackagedAsm)
	.settings(
		shared,
		javaOnly,
		withTesting
	)

lazy val FilterInjector = (project in file("filter-injector"))
	.dependsOn(RepackagedAsm, RepackagedMinlog, Util)
	.settings(
		shared,
		javaOnly
	)

lazy val Util = (project in file("util"))
	.dependsOn(RepackagedAsm, RepackagedMinlog)
	.settings(
		shared,
		javaOnly,
		withTesting
	)

lazy val Stack = (project in file("."))
	.settings(JarJarRunner.globalSettings)
	.aggregate(RepackagedAsm, RepackagedMinlog, Instrumentation, FilterInjector, Util)