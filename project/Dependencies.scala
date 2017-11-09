import sbt._

object Dependencies {
	lazy val minlog = "com.esotericsoftware" % "minlog" % "1.3.0"
	lazy val asm = Seq(
		"org.ow2.asm" % "asm" % "5.1",
		"org.ow2.asm" % "asm-commons" % "5.1"
	)

	// for testing
	lazy val scalactic = "org.scalactic" %% "scalactic" % "3.0.4" % Test
	lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4" % Test
	lazy val scalaMock = "org.scalamock" %% "scalamock" % "4.0.0" % Test
}