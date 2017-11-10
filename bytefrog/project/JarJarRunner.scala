import sbt._
import Keys._

import java.io.File

/** A helper object for running Jar Jar Links.
  *
  * @author robertf
  */
class JarJarRunner(java: File, jarjar: File) {
	def repackage(rulesFile: File, in: File, out: File) {
		val cmd =
			java.getCanonicalPath ::
			"-jar" :: jarjar.getCanonicalPath ::
			"process" :: rulesFile.getCanonicalPath :: in.getCanonicalPath :: out.getCanonicalPath ::
			Nil

		val ret = Process(cmd).!

		if (ret != 0) sys error s"Non-zero exit code from Jar Jar ($ret)"
	}

	override def toString() = s"JarJarRunner [java: $java, jarjar: $jarjar]"
}

object JarJarRunner {
	val jarjar = settingKey[File]("The location of jarjar.jar")

	def globalSettings = Seq(
		jarjar in Global := baseDirectory.value / "project" / "utils" / "jarjar.jar"
	)

	def asTask(javaHome: SettingKey[Option[File]]): Def.Initialize[Task[JarJarRunner]] = Def.task {
		new JarJarRunner((javaHome.value getOrElse file(System getProperty "java.home")) / "bin" / "java", jarjar.value)
	}

	def asTask(): Def.Initialize[Task[JarJarRunner]] = asTask(Keys.javaHome)
}