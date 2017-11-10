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
import DependencyFetcher._

import com.earldouglas.xsbtwebplugin.PluginKeys._
import sbt.classpath.ClasspathUtilities
import Project.Initialize

import sbtassembly.AssemblyPlugin.autoImport.assembly

import java.nio.file.{ Files, StandardCopyOption }

import com.avi.sbt.betterzip.BetterZip.{ Entry => ZipEntry, _ }

/** In charge of packaging up the node-webkit packages for distribution.
  *
  * @author robertf
  */
object Distributor extends BuildExtra {

	object Keys {
		val Distribution = config("distribution")

		val rootZipFolder = settingKey[String]("Root zip folder for distributions")
		val webappFolder = settingKey[String]("Webapp folder within zip")

		val distCommon = settingKey[File]("Common distribution files")
		val distJettyConf = settingKey[File]("Jetty configuration for distributions")

		val warContents = taskKey[Seq[(File, String)]]("Webapp war file contents")

		val webappClasses = taskKey[Seq[(File, String)]]("Find all .class files in the generated webapp classes folder")
		val webappClassesJar = taskKey[File]("Create a .jar file containing all of the webapp classes")

		val packageMappings = taskKey[Seq[ZipEntry]]("Generate zip file mappings for package.")

		val packageEmbeddedWin32 = taskKey[File]("Creates a zipped distribution of the node-webkit embedded version of the current project for Windows (32-bit)")
		val packageEmbeddedWin64 = taskKey[File]("Creates a zipped distribution of the node-webkit embedded version of the current project for Windows (64-bit)")
		val packageEmbeddedOsx = taskKey[File]("Creates a zipped distribution of the node-webkit embedded version of the current project for OS X (32/64-bit)")
		val packageEmbeddedLinuxX86 = taskKey[File]("Creates a zipped distribution of the node-webkit embedded version of the current project for Linux (x86)")
		val packageEmbeddedLinuxX64 = taskKey[File]("Creates a zipped distribution of the node-webkit embedded version of the current project for Linux (x64)")
	}

	type DependencyTask = Def.Initialize[Task[File]]

	object dependencies {
		object java {
			private val setOracleCookie: URLConnection => Unit = { _.setRequestProperty("Cookie", "oraclelicense=accept-securebackup-cookie") }
			private val trimPathRegex = raw"^\Qjre1.8.0_152\E(?:\.jre)?/".r
			private val trimPath: String => String = { trimPathRegex.replaceFirstIn(_, "") }

			val win32 = Dependency("jre.win32", "8u152", "http://download.oracle.com/otn-pub/java/jdk/8u152-b16/aa0333dd3019491ca4f6ddbe78cdb6d0/jre-8u152-windows-i586.tar.gz")
				.withConnectionStep(setOracleCookie)
				.extractAsTarGz { trimPath }
				.to { _ / "distrib-dependencies" / "win32" / "jre" }

			val win64 = Dependency("jre.win64", "8u152", "http://download.oracle.com/otn-pub/java/jdk/8u152-b16/aa0333dd3019491ca4f6ddbe78cdb6d0/jre-8u152-windows-x64.tar.gz")
				.withConnectionStep(setOracleCookie)
				.extractAsTarGz { trimPath }
				.to { _ / "distrib-dependencies" / "win64" / "jre" }

			val linuxX86 = Dependency("jre.linux-x86", "8u152", "http://download.oracle.com/otn-pub/java/jdk/8u152-b16/aa0333dd3019491ca4f6ddbe78cdb6d0/jre-8u152-linux-i586.tar.gz")
				.withConnectionStep(setOracleCookie)
				.extractAsTarGz { trimPath }
				.to { _ / "distrib-dependencies" / "linux-x86" / "jre" }

			val linuxX64 = Dependency("jre.linux-x64", "8u152", "http://download.oracle.com/otn-pub/java/jdk/8u152-b16/aa0333dd3019491ca4f6ddbe78cdb6d0/jre-8u152-linux-x64.tar.gz")
				.withConnectionStep(setOracleCookie)
				.extractAsTarGz { trimPath }
				.to { _ / "distrib-dependencies" / "linux-x64" / "jre" }

			val osx = Dependency("jre.osx", "8u152", "http://download.oracle.com/otn-pub/java/jdk/8u152-b16/aa0333dd3019491ca4f6ddbe78cdb6d0/jre-8u152-macosx-x64.tar.gz")
				.withConnectionStep(setOracleCookie)
				.extractAsTarGz { trimPath }
				.to { _ / "distrib-dependencies" / "osx" / "jre" }
		}

		object nwjs {
			private val trimPathRegex = raw"^nwjs-[^/]+/".r
			private val trimPath: String => String = { trimPathRegex.replaceFirstIn(_, "") }

			val win32 = Dependency("nwjs.win32", "v0.19.5", "https://dl.nwjs.io/v0.19.5/nwjs-v0.19.5-win-ia32.zip")
				.extractAsZip { trimPath }
				.to { _ / "distrib-dependencies" / "win32" / "nwjs" }

			val win64 = Dependency("nwjs.win64", "v0.19.5", "https://dl.nwjs.io/v0.19.5/nwjs-v0.19.5-win-x64.zip")
				.extractAsZip { trimPath }
				.to { _ / "distrib-dependencies" / "win64" / "nwjs" }

			val linuxX86 = Dependency("nwjs.linux-x86", "v0.19.5", "https://dl.nwjs.io/v0.19.5/nwjs-v0.19.5-linux-ia32.tar.gz")
				.extractAsTarGz { trimPath }
				.to { _ / "distrib-dependencies" / "linux-x86" / "nwjs" }

			val linuxX64 = Dependency("nwjs.linux-x64", "v0.19.5", "https://dl.nwjs.io/v0.19.5/nwjs-v0.19.5-linux-x64.tar.gz")
				.extractAsTarGz { trimPath }
				.to { _ / "distrib-dependencies" / "linux-x64" / "nwjs" }

			val osx = Dependency("nwjs.osx", "v0.19.5", "https://dl.nwjs.io/v0.19.5/nwjs-v0.19.5-osx-x64.zip")
				.extractAsZip { trimPath }
				.to { _ / "distrib-dependencies" / "osx" / "nwjs" }
		}


		val jetty = Dependency("jetty", "9.3.15.v20161220", "http://central.maven.org/maven2/org/eclipse/jetty/jetty-distribution/9.3.15.v20161220/jetty-distribution-9.3.15.v20161220.zip")
			.extractAsZip { raw"^\Qjetty-distribution-9.3.15.v20161220\E/".r.replaceFirstIn(_, "") }
			.to { _ / "distrib-dependencies" / "common" / "jetty" }

		object tools {
			val resourcer = Dependency("resourcer", "0.9", "https://dl.dropboxusercontent.com/s/zifogi9efgtsq1s/Anolis.Resourcer-0.9.zip?dl=1") // http://anolis.codeplex.com/downloads/get/81545
				.extractAsZip { identity }
				.to { _ / "tools" / "resourcer" }
		}
	}

	import Keys._

	object Settings {

		/** Each task of this type generates a listing of File->Path, where the File is
		  * a file in the filesystem that will be packaged in a zip/war/jar, and the Path
		  * is that file's path once it is packaged.
		  */
		type FileMappingTask = Def.Initialize[Task[Seq[(ZipEntry)]]]

		val webappClassesTask = Def.task {
			val classPath = raw"WEB-INF[\\/]classes[\\/](.*)".r

			(warContents in Distribution).value flatMap {
				case (f, p) =>
					p match {
						case classPath(relPath) => Some((f, relPath))
						case _ => None
					}
			}
		}

		val buildJarTask = Def.task {
			val jarToMake = crossTarget.value / (name.value + '-' + version.value + "-webapp-classes.jar")
			IO.jar((webappClasses in Distribution).value, jarToMake, new java.util.jar.Manifest)
			jarToMake
		}

		def embeddedWebApp(conf: Configuration, platform: String): FileMappingTask = Def.task {
			val path = (webappFolder in Distribution).value
			val appFolder = s"$path/webapps/root/"

			val jar = (webappClassesJar in Distribution).value
			val classSet = (webappClasses in Distribution).value.map(_._1).toSet
			val filteredContents = (warContents in Distribution).value.filterNot { case (file, _) => classSet contains file }
			var jarRec = (jar, (s"WEB-INF/lib/${jar.getName}"))

			appResource(platform, (rootZipFolder in Distribution).value,
			 (filteredContents ++ Seq(jarRec)) map {
				case (file, path) => (file, (appFolder + path.replace('\\', '/')))
			})
		}

		def jettyDist(platform: String): FileMappingTask = Def.task {
			val log = streams.value.log

			val webapp = (webappFolder in Distribution).value

			val jettyExclusions = Set(
				"demo-base/", "etc/", "start.d/", "start.ini"
			).map { exc => s"$webapp/$exc" }

			val jetty = dependencies.jetty.value
			val jettyFiles = jetty.*** pair rebase(jetty, webapp) map {
				// replace \ in paths with /, for easier matching below
				case (src, dest) => (src, dest.replace('\\', '/'))
			} filter {
				case (_, path) if jettyExclusions exists { path startsWith _ } => log.info(s"Excluding $path"); false
				case _ => true
			}

			val confDir = (distJettyConf in Distribution).value
			val jettyConf = confDir.*** pair rebase(confDir, webapp)

			appResource(platform, (rootZipFolder in Distribution).value, jettyFiles ++ jettyConf)
		}

		def embeddedAppFiles(platform: String): FileMappingTask = Def.task {
			val app = (distCommon in Distribution).value
			val root = (rootZipFolder in Distribution).value
			appResource(platform, root, app.*** pair rebase(app, root))
		}

		def nwjsRuntime(platform: String, nwjsDep: DependencyTask): FileMappingTask = Def.task {
			val log = streams.value.log

			val nwjs = nwjsDep.value

			val app = (distCommon in Distribution).value
			val root = (rootZipFolder in Distribution).value

			val nwkFiles: Seq[ZipEntry] = nwjs.*** pair rebase(nwjs, root) map {
				// replace \ in paths with /, for easier matching below
				case (src, dest) => (src, dest.replace('\\', '/'))
			}

			platform match {
				case "win32" | "win64" =>
					val inclusions = Set(
						"d3dcompiler_47.dll", "ffmpeg.dll", "icudtl.dat", "libEGL.dll", "libGLESv2.dll", "natives_blob.bin",
						"node.dll", "nw_100_percent.pak", "nw_200_percent.pak", "nw_elf.dll", "nw.dll", "resources.pak"
					).map { inc => s"$root/$inc" }

					nwkFiles flatMap {
						case ZipEntry(file, path, mode) if path == s"$root/nw.exe" =>
							// prepare our own copy of nw.exe with appropriately modified icon
							val customizedFile = target.value / "node-webkit" / platform / "codepulse.exe"
							customizedFile.getParentFile.mkdirs
							Files.copy(file.toPath, customizedFile.toPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)

							{
								import scala.sys.process._

								val ico = app / "app" / "icon.ico"

								val args = List(
									(dependencies.tools.resourcer.value / "Resourcer.exe").getCanonicalPath,
									"-op:upd",
									"-src:" + customizedFile.getCanonicalPath,
									"-type:14",
									"-name:IDR_MAINFRAME",
									"-file:" + ico.getCanonicalPath
								)

								if (args.! != 0)
									sys.error("Error running resourcer to update icon resource.")
							}

							Some(ZipEntry(customizedFile, s"$root/codepulse.exe", mode))

						case e @ ZipEntry(_, path, _) if inclusions contains path => Some(e)
						case e @ ZipEntry(_, path, _) if path startsWith s"$root/locales/" => Some(e)

						case ZipEntry(_, path, _) if path endsWith "/" => None // silent
						case ZipEntry(_, path, _) => log.info(s"Excluding $path"); None
					}

				case "osx" =>
					// on osx, rename node-webkit.app to Code Pulse.app
					def rewritePath(path: String) = s"$root/Code Pulse.app" + path.stripPrefix(s"$root/nwjs.app")

					nwkFiles flatMap {
						case ZipEntry(_, path, mode) if path == s"$root/nwjs.app/Contents/Resources/app.icns" =>
							// swap in our icon
							val icns = app / "app" / "icon.icns"
							Some(ZipEntry(icns, rewritePath(path), mode))

						case ZipEntry(file, path, mode) if path == s"$root/nwjs.app/Contents/Info.plist" =>
							// prepare our own Info.plist
							// pretty crappy using regular expressions for this, but plist files are absolutely awful to work with

							val replacements = List(
								"CFBundleName" -> "Code Pulse",
								"CFBundleDisplayName" -> "Code Pulse",
								"CFBundleVersion" -> version.value,
								"CFBundleShortVersionString" -> version.value
							)

							val plist = io.Source.fromFile(file).getLines.mkString("\n")

							val updatedPlist = replacements.foldLeft(plist) {
								case (last, (key, newVal)) =>
									val r = ("""(?<=<key>\Q""" + key + """\E</key>\s{0,15})<string>.*?</string>""").r
									r.replaceAllIn(last, "<string>" + newVal + "</string>")
							}

							val customizedInfo = target.value / "node-webkit" / "osx" / "Info.plist"
							customizedInfo.getParentFile.mkdirs

							{
								import java.io.PrintWriter
								val out = new PrintWriter(customizedInfo, "UTF-8")
								try { out.print(updatedPlist) }
								finally { out.close }
							}

							Some(ZipEntry(customizedInfo, rewritePath(path), mode))

						case ZipEntry(_, path, _) if path matches raw"$root/nwjs\.app/Contents/Resources/\w+\.lproj(?:/.*|$$)" => log.info(s"Excluding localization: $path"); None

						case ZipEntry(file, path, mode) if path startsWith s"$root/nwjs.app" =>
							Some(ZipEntry(file, rewritePath(path), mode))

						case ZipEntry(_, path, _) if path endsWith "/" => None // silent
						case ZipEntry(_, path, _) => log.info(s"Excluding $path"); None
					}

				case "linux-x86" | "linux-x64" =>
					val inclusions = Set(
						"lib/libffmpeg.so", "lib/libnode.so", "lib/libnw.so",
						"icudtl.dat", "natives_blob.bin", "nw_100_percent.pak", "nw_200_percent.pak", "resources.pak"
					).map { inc => s"$root/$inc" }

					nwkFiles flatMap {
						case ZipEntry(file, path, _) if path == s"$root/nw" => Some(ZipEntry(file, s"$root/codepulse", ExecutableType.Unix.mode)) // executable
						case e @ ZipEntry(_, path, _) if inclusions contains path => Some(e)
						case e @ ZipEntry(_, path, _) if path startsWith s"$root/locales/" => Some(e)

						case ZipEntry(_, path, _) if path endsWith "/" => None // silent
						case ZipEntry(_, path, _) => log.info(s"Excluding $path"); None
					}
			}
		}

		def javaRuntime(platform: String, jreDep: DependencyTask): FileMappingTask = Def.task {
			val log = streams.value.log

			val root = (rootZipFolder in Distribution).value
			val jreDest = s"$root/jre/"

			val jre = jreDep.value
			val jreFiles: Seq[ZipEntry] = appResource(platform, root, jre.*** pair rebase(jre, jreDest)) map {
				// replace \ in paths with /, for easier matching below
				case (src, dest) => (src, dest.replace('\\', '/'))
			}

			// exclude unnecessary files. this is platform dependant
			// I referenced <http://www.oracle.com/technetwork/java/javase/jdk-7-readme-429198.html#redistribution>
			// and <http://www.oracle.com/technetwork/java/javase/jre-7-readme-430162.html>
			// For Java 8: <http://www.oracle.com/technetwork/java/javase/jre-8-readme-2095710.html>
			platform match {
				case "win32" | "win64" =>
					val base = s"$root/jre/"

					val exclusions = Set(
						"bin/rmid.exe", "bin/rmiregistry.exe", "bin/tnameserv.exe", "bin/keytool.exe", "bin/policytool.exe", "bin/orbd.exe", "bin/servertool.exe",
						"bin/kinit.exe", "bin/klist.exe", "bin/ktab.exe",
						"bin/javaws.exe", "lib/javaws.jar",
						"bin/javaw.exe", "bin/javacpl.exe", "bin/javacpl.cpl", "bin/jucheck.exe", "bin/wsdetect.dll",
						"bin/npoji610.dll", "bin/axbridge.dll", "bin/deploy.dll", "bin/jpicom.dll",
						"bin/decora-sse.dll", "bin/fxplugins.dll", "bin/glass.dll", "bin/glib-lite.dll", "bin/gstreamer-lite.dll", "bin/javafx-font.dll", "bin/javafx-iio.dll", "bin/jfxmedia.dll", "bin/jfxwebkit.dll", "bin/libxml2.dll", "bin/libxslt.dll",
						"bin/jpiexp32.dll", "bin/jpinscp.dll", "bin/jpioji.dll",
						"lib/deploy.jar", "lib/plugin.jar", "lib/javaws.jar",
						"lib/javafx.properties", "lib/jfxrt.jar", "lib/security/javafx.policy",
						"lib/jfr", "lib/jfr.jar",
						"THIRDPARTYLICENSEREADME-JAVAFX.txt", "Welcome.html"
					).map { exc => s"$base/$exc" }

					val exclusionPatterns = Set(
						"bin/dtplugin/", "bin/plugin2/", "bin/server/",
						"bin/npjpi", // <bin/npjpi*.dll>
						"lib/deploy/", "lib/oblique-fonts/", "lib/desktop/", "plugin/"
					).map { exc => s"$base/$exc" }

					jreFiles filter {
						case ZipEntry(_, path, _) if (exclusions contains path) || exclusionPatterns.exists(path.startsWith) => log.info(s"Excluding $path"); false
						case _ => true
					}

				case "osx" =>
					val base = s"$root/Code Pulse.app/Contents/Resources/app.nw/jre/Contents/Home/"

					val exclusions = Set(
						"bin/rmid", "bin/rmiregistry", "bin/tnameserv", "bin/keytool", "bin/policytool", "bin/orbd", "bin/servertool",
						"lib/javafx.properties", "lib/jfxrt.jar", "lib/security/javafx.policy",
						"lib/fxplugins.dylib", "lib/libdecora-sse.dylib", "lib/libglass.dylib", "lib/libglib-2.0.0.dylib"," lib/libgstplugins-lite.dylib", "lib/libgstreamer-lite.dylib",
						"lib/libjavafx-font.dylib", "lib/libjavafx-iio.dylib", "lib/libjfxmedia.dylib", "lib/libjfxwebkit.dylib", "lib/libprism-es2.dylib",
						"lib/jfr", "lib/jfr.jar",
						"THIRDPARTYLICENSEREADME-JAVAFX.txt", "Welcome.html"
					).map { exc => s"$base/$exc" }

					val exclusionDirs = Set(
						"man/",
						"lib/oblique-fonts/", "lib/desktop/", "plugin/"
					).map { exc => s"$base/$exc" }

					jreFiles flatMap {
						case ZipEntry(file, path, _) if path == (s"$base/Contents/Home/bin/java") =>
							Some(ZipEntry(file, path, ExecutableType.Mac.mode)) // executable

						case e @ ZipEntry(_, path, _) if !(exclusions contains path) && !exclusionDirs.exists(path.startsWith) =>
							Some(e)

						case ZipEntry(_, path, _) if path endsWith "/" => None // silent
						case ZipEntry(_, path, _) => log.info(s"Excluding $path"); None
					}

				case "linux-x86" | "linux-x64" =>
					val base = s"$root/jre/"

					val exclusions = Set(
						"bin/rmid", "bin/rmiregistry", "bin/tnameserv", "bin/keytool", "bin/policytool", "bin/orbd", "bin/servertool",
						"bin/ControlPanel", "bin/javaws",
						"lib/javafx.properties", "lib/jfxrt.jar", "lib/security/javafx.policy",
						"lib/i386/fxavcodecplugin-52.so", "lib/i386/fxavcodecplugin-53.so", "lib/i386/fxplugins.so", "lib/i386/libglass.so", "lib/i386/libgstplugins-lite.so",
						"lib/i386/libgstreamer-lite.so", "lib/i386/libjavafx-font.so", "lib/i386/libjavafx-iio.so", "lib/i386/libjfxmedia.so", "lib/i386/libjfxwebkit.so", "lib/i386/libprism-es2.so",
						"lib/jfr", "lib/jfr.jar",
						"THIRDPARTYLICENSEREADME-JAVAFX.txt", "Welcome.html"
					).map { exc => s"$base/$exc" }

					val exclusionDirs = Set(
						"man/", "lib/deploy/",
						"lib/oblique-fonts/", "lib/desktop/", "plugin/"
					).map { exc => s"$base/$exc" }

					jreFiles flatMap {
						case ZipEntry(file, path, _) if path == (base + "bin/java") =>
							Some(ZipEntry(file, path, ExecutableType.Unix.mode)) // executable

						case e @ ZipEntry(_, path, _) if !(exclusions contains path) && !exclusionDirs.exists(path.startsWith) =>
							Some(e)

						case ZipEntry(_, path, _) if path endsWith "/" => None // silent
						case ZipEntry(_, path, _) => log.info(s"Excluding $path"); None
					}
			}
		}

		def agentJar(agent: Project, platform: String): FileMappingTask = Def.task {
			val root = (rootZipFolder in Distribution).value
			appResource(platform, root, List(((assembly in agent).value, s"$root/agent.jar")))
		}

		def appResource(platform: String, root: String, mappings: Seq[(File, String)]) = platform match {
			case "osx" =>
				// on osx, move things to be within the .app
				mappings map {
					case (file, path) =>
						val newPath = s"$root/Code Pulse.app/Contents/Resources/app.nw${path.stripPrefix(root)}"
						(file, newPath)
				}

			case _ => mappings
		}
	}

	import Settings._

	def distribution(platform: String, agent: Project, nwjs: DependencyTask, jre: DependencyTask, task: TaskKey[File])(config: Configuration) =
		Seq(packageMappings in (config, task) := embeddedWebApp(config, platform).value) ++
		inConfig(config) { Seq(
			packageMappings in task ++= jettyDist(platform).value,
			packageMappings in task ++= embeddedAppFiles(platform).value,
			packageMappings in task ++= nwjsRuntime(platform, nwjs).value,
			packageMappings in task ++= javaRuntime(platform, jre).value,
			packageMappings in task ++= agentJar(agent, platform).value,
			task := {
				val log = streams.value.log
				val name = s"CodePulse-${version.value}-$platform.zip"
				val out = crossTarget.value / name
				log.info(s"Packaging $name...")
				zip((packageMappings in task).value, out)
				log.info(s"Finished packaging $out")
				out
			}
		)}

	def distribSettings(agent: Project): Seq[Setting[_]] =
		Seq(
			rootZipFolder in Distribution := "codepulse",
			webappFolder in Distribution := s"${(rootZipFolder in Distribution).value}/backend",

			distCommon in Distribution := file("distrib/common"),
			distJettyConf in Distribution := file("distrib/jetty-conf"),

			webappClasses in Distribution := webappClassesTask.value,
			webappClassesJar in Distribution := buildJarTask.value
		) ++
		inConfig(DefaultConf) { warContents in Distribution := com.earldouglas.xsbtwebplugin.WarPlugin.packageWarTask(DefaultClasspathConf).value } ++
		distribution("win32", agent, dependencies.nwjs.win32, dependencies.java.win32, packageEmbeddedWin32)(Compile) ++
		distribution("win64", agent, dependencies.nwjs.win64, dependencies.java.win64, packageEmbeddedWin64)(Compile) ++
		distribution("osx", agent, dependencies.nwjs.osx, dependencies.java.osx, packageEmbeddedOsx)(Compile) ++
		distribution("linux-x86", agent, dependencies.nwjs.linuxX86, dependencies.java.linuxX86, packageEmbeddedLinuxX86)(Compile) ++
		distribution("linux-x64", agent, dependencies.nwjs.linuxX64, dependencies.java.linuxX64, packageEmbeddedLinuxX64)(Compile)
}