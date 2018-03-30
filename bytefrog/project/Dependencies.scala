import sbt._

object Dependencies {
	lazy val minlog = "com.esotericsoftware" % "minlog" % "1.3.0"
	lazy val asm = Seq(
		"org.ow2.asm" % "asm" % "6.0",
		"org.ow2.asm" % "asm-commons" % "6.0"
	)

	// for testing
	lazy val scalactic = "org.scalactic" %% "scalactic" % "3.0.4" % Test
	lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4" % Test
	lazy val scalaMock = "org.scalamock" %% "scalamock" % "4.0.0" % Test

	lazy val logback = "ch.qos.logback" % "logback-classic" % "0.9.26" % "compile->default"
	lazy val slf4j = "org.slf4j" % "jcl-over-slf4j" % "1.6.4"
}