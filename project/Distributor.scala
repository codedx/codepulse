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

import com.earldouglas.xsbtwebplugin.PluginKeys._
import sbt.classpath.ClasspathUtilities
import Project.Initialize

import sbtassembly.Plugin.AssemblyKeys.assembly

import java.nio.file.{ Files, StandardCopyOption }

import com.avi.sbt.betterzip.BetterZip.{ Entry => ZipEntry, _ }

/** In charge of packaging up the node-webkit packages for distribution.
  * 
  * @author robertf
  */
object Distributor extends BuildExtra {

	import DependencyFetcher.Keys._

	object Keys {
		val Distribution = config("distribution")

		val rootZipFolder = SettingKey[String]("root-zip-folder")
		val webappFolder = SettingKey[String]("webapp-folder")
		
		val distCommon = SettingKey[File]("dist-common")
		val distJettyConf = SettingKey[File]("dist-jetty-conf")

		val warContents = TaskKey[Seq[(File, String)]]("webapp-war-contents")

		val webappClasses = TaskKey[Seq[(File, String)]]("webapp-classes", "Find all .class files in the generated webapp classes folder")
		val webappClassesJar = TaskKey[File]("webapp-war-jar", "Create a .jar file containing all of the webapp classes")

		val packageMappings = TaskKey[Seq[ZipEntry]]("package-zip-mappings")
	}

	import Keys._
	
	object Settings {

		/** Each task of this type generates a listing of File->Path, where the File is
		  * a file in the filesystem that will be packaged in a zip/war/jar, and the Path
		  * is that file's path once it is packaged.
		  */
		type FileMappingTask = Initialize[Task[Seq[(ZipEntry)]]]

		def embeddedWar(conf: Configuration): FileMappingTask = (packageWar in conf, webappFolder in Distribution) map { (war, path) =>
			Seq(war -> (path + "webapps/root.war"))
		}

		def webappClassesTask: Initialize[Task[Seq[(File, String)]]] = (warContents in Distribution) map { (contents) =>
			val classPath = """WEB-INF[\\/]classes[\\/](.*)""".r

			contents flatMap {
				case (f, p) =>
					p match {
						case classPath(relPath) => Some((f, relPath))
						case _ => None
					}
			}
		}

		def buildJarTask: Initialize[Task[File]] = (webappClasses in Distribution, crossTarget, name, version) map { (classes, ct, name, version) =>
			val jarToMake = ct / (name + '-' + version + "-webapp-classes.jar")
			IO.jar(classes, jarToMake, new java.util.jar.Manifest)
			jarToMake
		}

		def embeddedWebApp(conf: Configuration, platform: String): FileMappingTask = (warContents in Distribution, rootZipFolder in Distribution, webappFolder in Distribution, webappClasses in Distribution, webappClassesJar in Distribution) map { (warContents, rootZipFolder, path, classes, jar) =>
			val appFolder = path + "webapps/root/"

			val classSet = classes.map(_._1).toSet
			val filteredContents = warContents.filterNot { case (file, _) => classSet contains file }
			var jarRec = (jar, ("WEB-INF/lib/" + jar.getName))

			appResource(platform, rootZipFolder, (filteredContents ++ Seq(jarRec)) map {
				case (file, path) => (file, (appFolder + path.replace('\\', '/')))
			})
		}

		def jettyDist(platform: String): FileMappingTask = (jetty in Dependencies, distJettyConf in Distribution, rootZipFolder in Distribution, webappFolder in Distribution) map { (jetty, confDir, rootZipFolder, webappFolder) => 
			if (!jetty.exists)
				sys.error("Missing jetty. Please run `fetch-package-dependencies` or download and place in " + jetty + ".")

			val jettyExclusions = List(
				"demo-base/", "etc/", "start.d/", "start.ini"
			).map(webappFolder + _)

			val jettyFiles = jetty.*** x rebase(jetty, webappFolder) map {
				// replace \ in paths with /, for easier matching below
				case (src, dest) => (src, dest.replace('\\', '/'))
			} filter {
				case (_, path) if jettyExclusions exists { path startsWith _ } => println("Excluding " + path); false
				case _ => true
			}

			val jettyConf = confDir.*** x rebase(confDir, webappFolder)

			appResource(platform, rootZipFolder, jettyFiles ++ jettyConf)
		}

		def embeddedAppFiles(platform: String): FileMappingTask = (distCommon in Distribution, rootZipFolder in Distribution) map { (appFolder, rootZipFolder) =>
			appResource(platform, rootZipFolder, appFolder.*** x rebase(appFolder, rootZipFolder))
		}

		def nodeWebkitRuntime(platform: String, nwkKey: SettingKey[File]): FileMappingTask = (nwkKey in Dependencies, resourcer in Dependencies, rootZipFolder in Distribution, distCommon in Distribution, target, version) map { (nwk, resourcer, rootZipFolder, appFolder, target, version) =>
			if (!nwk.exists)
				sys.error("Missing node-webkit for " + platform + ". Please run `fetch-package-dependencies` or download and place in " + nwk + ".")

			val nwkFiles: Seq[ZipEntry] = nwk.*** x rebase(nwk, rootZipFolder) map {
				// replace \ in paths with /, for easier matching below
				case (src, dest) => (src, dest.replace('\\', '/'))
			}

			platform match {
				case "win32" =>
					// on windows, we need nw.pak, icudt.dll, rename nw.exe -> codepulse.exe, libEGL/libGLES and the d3d DLLs
					val inclusions = List(
						"icudt.dll", "libEGL.dll", "libGLESv2.dll", "nw.pak"
					).map(rootZipFolder + _).toSet

					nwkFiles flatMap {
						case ZipEntry(file, path, mode) if path == "codepulse/nw.exe" =>
							// prepare our own copy of nw.exe with appropriately modified icon
							val customizedFile = target / "node-webkit" / "win32" / "codepulse.exe"
							customizedFile.getParentFile.mkdirs
							Files.copy(file.toPath, customizedFile.toPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)

							{
								import scala.sys.process._

								val ico = appFolder / "app" / "icon.ico"

								val args = List(
									(resourcer / "Resourcer.exe").getCanonicalPath,
									"-op:upd",
									"-src:" + customizedFile.getCanonicalPath,
									"-type:14",
									"-name:IDR_MAINFRAME",
									"-file:" + ico.getCanonicalPath
								)

								if (args.! != 0)
									sys.error("Error running resourcer to update icon resource.")
							}

							Some(ZipEntry(customizedFile, "codepulse/codepulse.exe", mode))

						case e @ ZipEntry(_, path, _) if inclusions contains path => Some(e)
						
						case ZipEntry(_, path, _) if path endsWith "/" => None // silent
						case ZipEntry(_, path, _) => println("Excluding " + path); None
					}

				case "osx" =>
					// on osx, rename node-webkit.app to Code Pulse.app
					def rewritePath(path: String) = "codepulse/Code Pulse.app" + path.stripPrefix("codepulse/node-webkit.app")

					nwkFiles flatMap {
						case ZipEntry(_, path, mode) if path == "codepulse/node-webkit.app/Contents/Resources/nw.icns" =>
							// swap in our icon
							val icns = appFolder / "app" / "icon.icns"
							Some(ZipEntry(icns, rewritePath(path), mode))

						case ZipEntry(file, path, mode) if path == "codepulse/node-webkit.app/Contents/Info.plist" =>
							// prepare our own Info.plist
							// pretty crappy using regular expressions for this, but plist files are absolutely awful to work with

							val replacements = List(
								"CFBundleName" -> "Code Pulse",
								"CFBundleDisplayName" -> "Code Pulse",
								"CFBundleVersion" -> version,
								"CFBundleShortVersionString" -> version
							)

							val plist = io.Source.fromFile(file).getLines.mkString("\n")

							val updatedPlist = replacements.foldLeft(plist) {
								case (last, (key, newVal)) =>
									val r = ("""(?<=<key>\Q""" + key + """\E</key>\s{0,15})<string>.*?</string>""").r
									r.replaceAllIn(last, "<string>" + newVal + "</string>")
							}

							val customizedInfo = target / "node-webkit" / "osx" / "Info.plist"
							customizedInfo.getParentFile.mkdirs

							{
								import java.io.PrintWriter
								val out = new PrintWriter(customizedInfo, "UTF-8")
								try { out.print(updatedPlist) }
								finally { out.close }
							}

							Some(ZipEntry(customizedInfo, rewritePath(path), mode))

						case ZipEntry(file, path, mode) if path startsWith "codepulse/node-webkit.app" =>
							Some(ZipEntry(file, rewritePath(path), mode))
						
						case ZipEntry(_, path, _) if path endsWith "/" => None // silent
						case ZipEntry(_, path, _) => println("Excluding " + path); None
					}

				case "linux-x86" =>
					// on linux, just keep nw and nw.pak. we don't need media features, so we can skip libffmpeg.so
					nwkFiles flatMap {
						case ZipEntry(file, path, _) if path == "codepulse/nw" => Some(ZipEntry(file, "codepulse/codepulse", Some(755))) // executable
						case e @ ZipEntry(_, path, _) if path == "codepulse/nw.pak" => Some(e)

						case ZipEntry(_, path, _) if path endsWith "/" => None // silent
						case ZipEntry(_, path, _) => println("Excluding " + path); None
					}
			}
		}

		def javaRuntime(platform: String, jreKey: SettingKey[File]): FileMappingTask = (jreKey in Dependencies, rootZipFolder in Distribution) map { (jre, rootZipFolder) =>
			val jreDest = rootZipFolder + "jre/"

			if (!jre.exists)
				sys.error("Missing JRE for " + platform + ". Please run `fetch-package-dependencies` or download and place in " + jre + ".")

			val jreFiles: Seq[ZipEntry] = appResource(platform, rootZipFolder, jre.*** x rebase(jre, jreDest)) map {
				// replace \ in paths with /, for easier matching below
				case (src, dest) => (src, dest.replace('\\', '/'))
			}

			// exclude unnecessary files. this is platform dependant
			// I referenced <http://www.oracle.com/technetwork/java/javase/jdk-7-readme-429198.html#redistribution>
			// and <http://www.oracle.com/technetwork/java/javase/jre-7-readme-430162.html>
			platform match {
				case "win32" =>
					val base = rootZipFolder + "jre/"

					val exclusions = List(
						"bin/rmid.exe", "bin/rmiregistry.exe", "bin/tnameserv.exe", "bin/keytool.exe", "bin/policytool.exe", "bin/orbd.exe", "bin/servertool.exe",
						"bin/kinit.exe", "bin/klist.exe", "bin/ktab.exe",
						"bin/javaws.exe", "lib/javaws.jar",
						"bin/javaw.exe", "bin/javacpl.exe", "bin/javacpl.cpl", "bin/jucheck.exe", "bin/wsdetect.dll",
						"bin/npoji610.dll", "bin/axbridge.dll", "bin/deploy.dll", "bin/jpicom.dll",
						"bin/decora-sse.dll", "bin/fxplugins.dll", "bin/glass.dll", "bin/glib-lite.dll", "bin/gstreamer-lite.dll", "bin/javafx-font.dll", "bin/javafx-iio.dll", "bin/jfxmedia.dll", "bin/jfxwebkit.dll", "bin/libxml2.dll", "bin/libxslt.dll",
						"bin/jpiexp32.dll", "bin/jpinscp.dll", "bin/jpioji.dll",
						"lib/deploy.jar", "lib/plugin.jar", "lib/javaws.jar",
						"lib/javafx.properties", "lib/jfxrt.jar", "lib/security/javafx.policy",
						"THIRDPARTYLICENSEREADME-JAVAFX.txt", "Welcome.html"
					).map(base + _).toSet

					val exclusionPatterns = List(
						"bin/dtplugin/", "bin/plugin2/", "bin/server/",
						"bin/npjpi", // <bin/npjpi*.dll>
						"lib/deploy/"
					).map(base + _)

					jreFiles filter {
						case ZipEntry(_, path, _) if (exclusions contains path) || exclusionPatterns.exists(path.startsWith) => println("Excluding " + path); false
						case _ => true
					}

				case "osx" =>
					val base = rootZipFolder + "Code Pulse.app/Contents/Resources/app.nw/jre/Contents/Home/"

					val exclusions = List(
						"bin/rmid", "bin/rmiregistry", "bin/tnameserv", "bin/keytool", "bin/policytool", "bin/orbd", "bin/servertool",
						"lib/javafx.properties", "lib/jfxrt.jar", "lib/security/javafx.policy",
						"lib/fxplugins.dylib", "lib/libdecora-sse.dylib","lib/libglass.dylib","lib/libglib-2.0.0.dylib","lib/libgstplugins-lite.dylib","lib/libgstreamer-lite.dylib","lib/libjavafx-font.dylib","lib/libjavafx-iio.dylib","lib/libjfxmedia.dylib","lib/libjfxwebkit.dylib","lib/libprism-es2.dylib",
						"THIRDPARTYLICENSEREADME-JAVAFX.txt", "Welcome.html"
					).map(base + _).toSet

					val exclusionDirs = List(
						"man/"
					).map(base + _)

					jreFiles flatMap {
						case ZipEntry(file, path, _) if path == (base + "Contents/Home/bin/java") =>
							Some(ZipEntry(file, path, Some(755))) // executable

						case e @ ZipEntry(_, path, _) if !(exclusions contains path) && !exclusionDirs.exists(path.startsWith) =>
							Some(e)
						
						case ZipEntry(_, path, _) if path endsWith "/" => None // silent
						case ZipEntry(_, path, _) => println("Excluding " + path); None
					}

				case "linux-x86" =>
					val base = rootZipFolder + "jre/"

					val exclusions = List(
						"bin/rmid", "bin/rmiregistry", "bin/tnameserv", "bin/keytool", "bin/policytool", "bin/orbd", "bin/servertool",
						"bin/ControlPanel", "bin/javaws",
						"lib/javafx.properties", "lib/jfxrt.jar", "lib/security/javafx.policy",
						"lib/i386/fxavcodecplugin-52.so", "lib/i386/fxavcodecplugin-53.so", "lib/i386/fxplugins.so", "lib/i386/libglass.so", "lib/i386/libgstplugins-lite.so", "lib/i386/libgstreamer-lite.so", "lib/i386/libjavafx-font.so", "lib/i386/libjavafx-iio.so", "lib/i386/libjfxmedia.so", "lib/i386/libjfxwebkit.so", "lib/i386/libprism-es2.so", 
						"THIRDPARTYLICENSEREADME-JAVAFX.txt", "Welcome.html"
					).map(base + _).toSet

					val exclusionDirs = List(
						"man/", "lib/deploy/"
					).map(base + _)

					jreFiles flatMap {
						case ZipEntry(file, path, _) if path == (base + "bin/java") =>
							Some(ZipEntry(file, path, Some(755))) // executable

						case e @ ZipEntry(_, path, _) if !(exclusions contains path) && !exclusionDirs.exists(path.startsWith) =>
							Some(e)
						
						case ZipEntry(_, path, _) if path endsWith "/" => None // silent
						case ZipEntry(_, path, _) => println("Excluding " + path); None
					}
			}
		}

		def agentJar(platform: String): FileMappingTask = (assembly in BuildDef.BytefrogAgent, rootZipFolder in Distribution) map { (agentJar, rootZipFolder) =>
			appResource(platform, rootZipFolder, List((agentJar, rootZipFolder + "agent.jar")))
		}

		def appResource(platform: String, rootZipFolder: String, mappings: Seq[(File, String)]) = platform match {
			case "osx" =>
				// on osx, move things to be within the .app
				mappings map {
					case (file, path) =>
						val newPath = rootZipFolder + "Code Pulse.app/Contents/Resources/app.nw/" + path.stripPrefix(rootZipFolder)
						(file, newPath)
				}

			case _ => mappings
		}
	}

	import Settings._

	def packageEmbeddedSettingsIn(platform: String, nwkKey: SettingKey[File], jreKey: SettingKey[File], task: TaskKey[File])(config: Configuration) =
		Seq(packageMappings in (config, task) <<= embeddedWebApp(config, platform)) ++
		inConfig(config) { Seq(
			packageMappings in task <++= jettyDist(platform),
			packageMappings in task <++= embeddedAppFiles(platform),
			packageMappings in task <++= nodeWebkitRuntime(platform, nwkKey),
			packageMappings in task <++= javaRuntime(platform, jreKey),
			packageMappings in task <++= agentJar(platform),
			task <<= (packageMappings in task, crossTarget, version, streams) map { (mappings, crossTarget, ver, streams) =>
				val name = "CodePulse-" + ver + "-" + platform + ".zip"
				val out = crossTarget / name
				streams.log.info("Packaging " + name + "...")
				zip(mappings, out)
				streams.log.info("Finished packaging " + out)
				out
			}
		)}
	
	lazy val distribSettings: Seq[Setting[_]] = 
		Seq(
			rootZipFolder in Distribution := "codepulse/",
			webappFolder in Distribution <<= (rootZipFolder in Distribution) { _ + "backend/" },
			
			distCommon in Distribution := file("distrib/common"),
			distJettyConf in Distribution := file("distrib/jetty-conf"),
			
			webappClasses in Distribution <<= webappClassesTask,
			webappClassesJar in Distribution <<= buildJarTask
		) ++ 
		inConfig(DefaultConf) { warContents in Distribution <<= com.earldouglas.xsbtwebplugin.WarPlugin.packageWarTask(DefaultClasspathConf) } ++
		packageEmbeddedSettingsIn("win32", nwkWindows, jreWindows, packageEmbeddedWin32)(Compile) ++
		packageEmbeddedSettingsIn("osx", nwkOsx, jreOsx, packageEmbeddedOsx)(Compile) ++
		packageEmbeddedSettingsIn("linux-x86", nwkLinux, jreLinux, packageEmbeddedLinuxX86)(Compile)
}