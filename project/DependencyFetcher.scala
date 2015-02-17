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
import Project.Initialize

import scala.collection.JavaConversions._

import java.io.{ BufferedInputStream, InputStream, File, FileInputStream, FileOutputStream }
import java.net.{ HttpURLConnection, URL, URLConnection }

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.io.IOUtils

/** Helper for fetching various Code Pulse dependencies from the web.
  *
  * @author robertf
  */
object DependencyFetcher extends BuildExtra {

	sealed trait Platform
	object Platform {
		case object Unspecified extends Platform
		case object Windows extends Platform
		case object Linux extends Platform
		case object OSX extends Platform
	}

	sealed trait FileFormat
	object FileFormat {
		case object Raw extends FileFormat
		case object Zip extends FileFormat
		case object TarGz extends FileFormat
	}

	case class DependencyFile(platform: Platform, url: String, destPath: File, format: FileFormat)

	sealed trait Dependency { def name: String }
	sealed trait PackageHelper { def trimPath(platform: Platform)(path: String): String }
	case class JreDependency(name: String, rawPath: String, files: List[DependencyFile]) extends Dependency with PackageHelper {
		private val trimPathRegex = ("^\\Q" + rawPath + "\\E(?:\\.jre)?/").r
		def trimPath(platform: Platform)(path: String) = trimPathRegex.replaceFirstIn(path, "")
	}
	case class PlatformDependency(name: String, files: List[DependencyFile]) extends Dependency
	case class CommonDependency(name: String, file: DependencyFile) extends Dependency
	case class ToolDependency(name: String, file: DependencyFile) extends Dependency

	val BufferLen = 4096

	object Keys {
		val Dependencies = config("dependencies")

		val dependencyFolder = SettingKey[File]("deps-folder")

		val jreWindows = SettingKey[File]("jre-windows")
		val jreLinux = SettingKey[File]("jre-linux")
		val jreOsx = SettingKey[File]("jre-osx")

		val jreWindowsUrl = SettingKey[String]("jre-windows-url")
		val jreLinuxUrl = SettingKey[String]("jre-linux-url")
		val jreOsxUrl = SettingKey[String]("jre-osx-url")

		val nwkWindows = SettingKey[File]("nwk-windows")
		val nwkLinux = SettingKey[File]("nwk-linux")
		val nwkOsx = SettingKey[File]("nwk-osx")

		val nwkWindowsUrl = SettingKey[String]("nwk-windows-url")
		val nwkLinuxUrl = SettingKey[String]("nwk-linux-url")
		val nwkOsxUrl = SettingKey[String]("nwk-osx-url")

		val jetty = SettingKey[File]("jetty")
		val dependencyCheck = SettingKey[File]("dependency-check")
		val resourcer = SettingKey[File]("resourcer")

		val jettyUrl = SettingKey[String]("jetty-url")
		val resourcerUrl = SettingKey[String]("resourcer-url")

		val packageDependencyList = TaskKey[Seq[Dependency]]("package-dependencies")
	}

	import Keys._

	private class ProgressInputStream(underlying: InputStream, message: String, size: Long) extends InputStream {
		private var bytesRead = 0L
		private def progress(chunk: Int) {
			bytesRead += chunk
			val pct = 100 * bytesRead / size
			print(message + "... " + pct + "% complete\r")
		}

		override def read() = {
			val b = underlying.read
			if (b != -1) progress(1)
			b
		}

		override def read(b: Array[Byte]) = {
			val chunk = underlying.read(b)
			if (chunk >= 0) progress(chunk)
			chunk
		}

		override def read(b: Array[Byte], off: Int, len: Int) = {
			val chunk = underlying.read(b, off, len)
			if (chunk >= 0) progress(chunk)
			chunk
		}

		override def available() = underlying.available
		override def close() = underlying.close
		override def mark(readlimit: Int) = underlying.mark(readlimit)
		override def markSupported() = underlying.markSupported
		override def reset() = underlying.reset
		override def skip(n: Long) = underlying.skip(n)
	}

	private def doDownload(label: String, log: Logger, dep: Dependency, file: DependencyFile, dest: File, preConnect: URLConnection => Unit = _ => ()) {
		val conn = {
			def connect(url: URL): HttpURLConnection = {
				val conn = url.openConnection.asInstanceOf[HttpURLConnection]
				preConnect(conn)

				conn setInstanceFollowRedirects true
				conn setDoInput true

				conn.connect

				val status = conn.getResponseCode
				status match {
					case HttpURLConnection.HTTP_MOVED_TEMP | HttpURLConnection.HTTP_MOVED_PERM | HttpURLConnection.HTTP_SEE_OTHER =>
						val redirect = conn getHeaderField "Location"
						connect(new URL(redirect))

					case _ => conn
				}
			}

			connect(new URL(file.url))
		}

		val stream = new BufferedInputStream(
			new ProgressInputStream(conn.getInputStream, "Downloading " + label, conn.getContentLengthLong)
		)

		try {
			lazy val pathTrimmer: String => String = dep match {
				case ph: PackageHelper => ph.trimPath(file.platform)
				case _ => identity
			}

			file.format match {
				case FileFormat.Raw => processRaw(stream, dest)
				case FileFormat.Zip => processZip(stream, dest, pathTrimmer)
				case FileFormat.TarGz => processTarGz(stream, dest, pathTrimmer)
			}
		} finally {
			IOUtils closeQuietly stream
		}

		// "Downloading <label>... xxx% complete" = 18 extra characters to clear
		log.info("Downloaded " + label + "                  ")
	}

	private def processRaw(stream: InputStream, destFile: File) {
		val fos = new FileOutputStream(destFile)
		try {
			IOUtils.copyLarge(stream, fos)
		} finally {
			IOUtils closeQuietly fos
		}
	}

	private def processZip(stream: InputStream, destFolder: File, pathTrim: String => String) {
		destFolder.mkdirs

		val zin = new ZipArchiveInputStream(stream)

		try {
			val entries = Iterator.continually { zin.getNextZipEntry }.takeWhile { _ != null }
			val buffer = new Array[Byte](BufferLen)

			for (entry <- entries if !entry.isDirectory) {
				val out = destFolder / pathTrim(entry.getName)
				out.getParentFile.mkdirs

				val fos = new FileOutputStream(out)
				try {
					val reads = Iterator.continually { zin.read(buffer, 0, BufferLen) }.takeWhile { _ > 0 }
					for (read <- reads) fos.write(buffer, 0, read)
				} finally {
					IOUtils closeQuietly fos
				}

				out setLastModified entry.getLastModifiedDate.getTime
			}
		} finally {
			IOUtils closeQuietly zin
		}
	}

	private def processTarGz(stream: InputStream, destFolder: File, pathTrim: String => String) {
		destFolder.mkdirs

		val gzin = new GzipCompressorInputStream(stream)
		val tarin = new TarArchiveInputStream(gzin)

		try {
			val entries = Iterator.continually { tarin.getNextTarEntry }.takeWhile { _ != null }
			val buffer = new Array[Byte](BufferLen)

			for (entry <- entries if !entry.isDirectory) {
				val out = destFolder / pathTrim(entry.getName)
				out.getParentFile.mkdirs

				val fos = new FileOutputStream(out)
				try {
					val reads = Iterator.continually { tarin.read(buffer, 0, BufferLen) }.takeWhile { _ > 0 }
					for (read <- reads) fos.write(buffer, 0, read)
				} finally {
					IOUtils closeQuietly fos
				}

				out setLastModified entry.getLastModifiedDate.getTime
			}
		} finally {
			IOUtils closeQuietly tarin
			IOUtils closeQuietly gzin
		}
	}

	object Settings {
		def fetchDependenciesTask(dependencyList: TaskKey[Seq[Dependency]]): Def.Initialize[Task[Unit]] = (dependencyList in Dependencies, streams) map { (deps, streams) =>
			val log = streams.log

			deps foreach {
				case dep @ JreDependency(name, _, files) =>
					for (file @ DependencyFile(platform, _, destPath, _) <- files)
						doDownload(
							name + " [" + platform + "]", log,
							dep, file, destPath,
							{ _.setRequestProperty("Cookie", "oraclelicense=accept-securebackup-cookie") }
						)

				case dep @ PlatformDependency(name, files) =>
					for (file @ DependencyFile(platform, _, destPath, _) <- files)
						doDownload(
							name + " [" + platform + "]", log,
							dep, file, destPath
						)

				case dep @ CommonDependency(name, file @ DependencyFile(_, _, destPath, _)) =>
					doDownload(
						name + " [common]", log,
						dep, file, destPath
					)

				case dep @ ToolDependency(name, file @ DependencyFile(_, _, destPath, _)) =>
					doDownload(
						name + " [tool]", log,
						dep, file, destPath
					)
			}
		}
	}

	import Settings._

	lazy val dependencyFetcherSettings: Seq[Setting[_]] = Seq(
		dependencyFolder in Dependencies := file("distrib/dependencies"),

		packageDependencyList in Dependencies := Nil,

		jreWindowsUrl in Dependencies := "http://download.oracle.com/otn-pub/java/jdk/7u55-b13/jre-7u55-windows-i586.tar.gz",
		jreWindows in Dependencies <<= (dependencyFolder in Dependencies) { _ / "win32" / "jre" },
		jreLinuxUrl in Dependencies := "http://download.oracle.com/otn-pub/java/jdk/7u55-b13/jre-7u55-linux-i586.tar.gz",
		jreLinux in Dependencies <<= (dependencyFolder in Dependencies) { _ / "linux-x86" / "jre" },
		jreOsxUrl in Dependencies := "http://download.oracle.com/otn-pub/java/jdk/7u55-b13/jre-7u55-macosx-x64.tar.gz",
		jreOsx in Dependencies <<= (dependencyFolder in Dependencies) { _ / "osx" / "jre" },

		packageDependencyList in Dependencies <+= (jreWindowsUrl in Dependencies, jreWindows in Dependencies, jreLinuxUrl in Dependencies, jreLinux in Dependencies, jreOsxUrl in Dependencies, jreOsx in Dependencies) map { (jreWinUrl, jreWin, jreLinUrl, jreLin, jreOsxUrl, jreOsx) =>
			JreDependency(
				name = "jre 7u55", rawPath = "jre1.7.0_55",
				files = List(
					DependencyFile(Platform.Windows, jreWinUrl, jreWin, FileFormat.TarGz),
					DependencyFile(Platform.Linux, jreLinUrl, jreLin, FileFormat.TarGz),
					DependencyFile(Platform.OSX, jreOsxUrl, jreOsx, FileFormat.TarGz)
				)
			)
		},

		nwkWindows in Dependencies <<= (dependencyFolder in Dependencies) { _ / "win32" / "node-webkit" },
		nwkWindowsUrl in Dependencies := "http://dl.node-webkit.org/v0.9.2/node-webkit-v0.9.2-win-ia32.zip",
		nwkLinux in Dependencies <<= (dependencyFolder in Dependencies) { _ / "linux-x86" / "node-webkit" },
		nwkLinuxUrl in Dependencies := "http://dl.node-webkit.org/v0.9.2/node-webkit-v0.9.2-linux-ia32.tar.gz",
		nwkOsx in Dependencies <<= (dependencyFolder in Dependencies) { _ / "osx" / "node-webkit" },
		nwkOsxUrl in Dependencies := "http://dl.node-webkit.org/v0.9.2/node-webkit-v0.9.2-osx-ia32.zip",

		packageDependencyList in Dependencies <+= (nwkWindowsUrl in Dependencies, nwkWindows in Dependencies, nwkLinuxUrl in Dependencies, nwkLinux in Dependencies, nwkOsxUrl in Dependencies, nwkOsx in Dependencies) map { (nwkWinUrl, nwkWin, nwkLinUrl, nwkLin, nwkOsxUrl, nwkOsx) =>
			new PlatformDependency(
				name = "node-webkit v0.9.2",
				files = List(
					DependencyFile(Platform.Windows, nwkWinUrl, nwkWin, FileFormat.Zip),
					DependencyFile(Platform.Linux, nwkLinUrl, nwkLin, FileFormat.TarGz),
					DependencyFile(Platform.OSX, nwkOsxUrl, nwkOsx, FileFormat.Zip)
				)
			) with PackageHelper {
				private val trimPathRegex = ("^\\Qnode-webkit-v0.9.2-linux-ia32\\E/").r
				def trimPath(platform: Platform)(path: String) = platform match {
					case Platform.Linux => trimPathRegex.replaceFirstIn(path, "")
					case _ => path
				}
			}
		},

		jetty in Dependencies <<= (dependencyFolder in Dependencies) { _ / "common" / "jetty" },
		jettyUrl in Dependencies := "http://mirrors.xmission.com/eclipse/jetty/9.1.4.v20140401/dist/jetty-distribution-9.1.4.v20140401.zip",
		resourcer in Dependencies <<= (dependencyFolder in Dependencies) { _ / "tools" / "resourcer" },
		resourcerUrl in Dependencies := "https://dl.dropboxusercontent.com/s/zifogi9efgtsq1s/Anolis.Resourcer-0.9.zip?dl=1", // http://anolis.codeplex.com/downloads/get/81545

		packageDependencyList in Dependencies <++= (jettyUrl in Dependencies, jetty in Dependencies, resourcerUrl in Dependencies, resourcer in Dependencies) map { (jettyUrl, jetty, resourcerUrl, resourcer) =>
			val jettyDep = new CommonDependency("Jetty 9.1.4 v20140401", DependencyFile(Platform.Unspecified, jettyUrl, jetty, FileFormat.Zip)) with PackageHelper {
				private val trimPathRegex = ("^\\Qjetty-distribution-9.1.4.v20140401\\E/").r
				def trimPath(platform: Platform)(path: String) = {
					trimPathRegex.replaceFirstIn(path, "")
				}
			}

			val resourcerDep = ToolDependency("Resourcer", DependencyFile(Platform.Unspecified, resourcerUrl, resourcer, FileFormat.Zip))

			jettyDep :: resourcerDep :: Nil
		},

		fetchPackageDependencies <<= fetchDependenciesTask(packageDependencyList)
	)
}