import sbt._
import Keys._

/** Repackages a dependency using Jar Jar Links.
  *
  * @author robertf
  */
class Repackager(
	name: String,
	deps: Seq[ModuleID],
	rules: Repackager.Rule*
) {
	import Repackager.{ jarjarRunner, repackage }

	val RepackagerConfig = config(s"repackager-$name").hide

	val settings = Seq(
		ivyConfigurations += RepackagerConfig,
		libraryDependencies ++= deps.map(_ % RepackagerConfig),

		jarjarRunner in RepackagerConfig := JarJarRunner.asTask(javaHome in RepackagerConfig).value,

		repackage in RepackagerConfig := {
			val taskStreams = streams.value
			val log = taskStreams.log
			val cache = taskStreams.cacheDirectory

			val outDir = target.value / s"repackaged-$name"
			val jjRunner = (jarjarRunner in RepackagerConfig).value

			outDir.mkdirs

			//TODO: make cache dependent on rules as well?
			val cachedRepackage = FileFunction.cached(cache / s"repackage-$name", inStyle = FilesInfo.lastModified, outStyle = FilesInfo.exists) { (jars: Set[File]) =>
				IO.withTemporaryFile("jarjar-", ".rules") { ruleFile =>
					IO.writeLines(ruleFile, rules.map(_.line))

					val outs = for (jar <- jars) yield {
						val out = outDir / s"repackaged-${jar.getName}"

						log info s"Repackaging ${jar.getName}..."
						jjRunner.repackage(ruleFile, jar, out)

						out
					}

					outs.toSet
				}
			}

			cachedRepackage(Classpaths.managedJars(RepackagerConfig, classpathTypes.value, update.value).map(_.data).toSet).toSeq
		},

		exportedProducts in Compile ++= (repackage in RepackagerConfig).value
	)
}

object Repackager {
	def apply(name: String, deps: Seq[ModuleID], rules: Rule*) = new Repackager(name, deps, rules: _*)
	def apply(name: String, dep: ModuleID, rules: Rule*) = new Repackager(name, Seq(dep), rules: _*)

	sealed trait Rule { def line: String }

	case class Rename(pattern: String, result: String) extends Rule {
		def line = s"rule $pattern $result"
	}

	case class Zap(pattern: String) extends Rule {
		def line = s"zap $pattern"
	}

	case class Keep(pattern: String) extends Rule {
		def line = s"keep $pattern"
	}


	val jarjarRunner = TaskKey[JarJarRunner]("jarjar-runner")
	val repackage = TaskKey[Seq[File]]("repackage")
}