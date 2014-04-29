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

/** Helper for fetching the Code Pulse dependencies from the web.
  *
  * @author robertf
  */
object DependencyFetcher extends BuildExtra {

	import Distributor.Keys.{ Distribution, distDeps }

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

	case class DependencyFile(platform: Platform, url: String, format: FileFormat)

	sealed trait Dependency { def name: String; def destPath: String }
	sealed trait PackageHelper { def trimPath(platform: Platform)(path: String): String }
	case class JreDependency(name: String, rawPath: String, destPath: String, files: List[DependencyFile]) extends Dependency with PackageHelper {
		private val trimPathRegex = ("^\\Q" + rawPath + "\\E(?:\\.jre)?/").r
		def trimPath(platform: Platform)(path: String) = trimPathRegex.replaceFirstIn(path, "")
	}
	case class PlatformDependency(name: String, destPath: String, files: List[DependencyFile]) extends Dependency
	case class CommonDependency(name: String, destPath: String, file: DependencyFile) extends Dependency
	case class ToolDependency(name: String, destPath: String, file: DependencyFile) extends Dependency

	val BufferLen = 4096

	object Keys {
		val PackageDependencies = config("package-dependencies")

		val jreWindows = SettingKey[String]("jre-windows")
		val jreLinux = SettingKey[String]("jre-linux")
		val jreOsx = SettingKey[String]("jre-osx")

		val nwkWindows = SettingKey[String]("nwk-windows")
		val nwkLinux = SettingKey[String]("nwk-linux")
		val nwkOsx = SettingKey[String]("nwk-osx")

		val jetty = SettingKey[String]("jetty")
		val resourcer = SettingKey[String]("resourcer")

		val dependencyList = TaskKey[Seq[Dependency]]("dependency-list")
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
		val fetchDependenciesTask: Initialize[Task[Unit]] = (distDeps in Distribution, dependencyList in PackageDependencies, streams) map { (distDepsFolder, deps, streams) =>
			val log = streams.log

			val commonFolder = distDepsFolder / "common"
			val toolsFolder = distDepsFolder / "tools"
			def getPlatformFolder(platform: Platform) = platform match {
				case Platform.Windows => distDepsFolder / "win32"
				case Platform.Linux => distDepsFolder / "linux-x86"
				case Platform.OSX => distDepsFolder / "osx"
				case Platform.Unspecified => commonFolder
			}

			deps foreach {
				case dep @ JreDependency(name, _, destPath, files) =>
					for (file @ DependencyFile(platform, url, _) <- files)
						doDownload(
							name + " [" + platform + "]", log,
							dep, file,
							getPlatformFolder(platform) / destPath,
							{ _.setRequestProperty("Cookie", "oraclelicense=accept-securebackup-cookie") }
						)

				case dep @ PlatformDependency(name, destPath, files) =>
					for (file @ DependencyFile(platform, url, _) <- files)
						doDownload(
							name + " [" + platform + "]", log,
							dep, file,
							getPlatformFolder(platform) / destPath
						)

				case dep @ CommonDependency(name, destPath, file) =>
					doDownload(
						name + " [common]", log,
						dep, file,
						commonFolder / destPath
					)

				case dep @ ToolDependency(name, destPath, file) =>
					doDownload(
						name + " [tool]", log,
						dep, file,
						toolsFolder / destPath
					)
			}
		}
	}

	import Settings._

	lazy val dependencyFetcherSettings: Seq[Setting[_]] = Seq(
		dependencyList in PackageDependencies := Nil,

		jreWindows in PackageDependencies := "http://download.oracle.com/otn-pub/java/jdk/7u55-b13/jre-7u55-windows-i586.tar.gz",
		jreLinux in PackageDependencies := "http://download.oracle.com/otn-pub/java/jdk/7u55-b13/jre-7u55-linux-i586.tar.gz",
		jreOsx in PackageDependencies := "http://download.oracle.com/otn-pub/java/jdk/7u55-b13/jre-7u55-macosx-x64.tar.gz",
		dependencyList in PackageDependencies <+= (jreWindows in PackageDependencies, jreLinux in PackageDependencies, jreOsx in PackageDependencies) map { (jreWin, jreLin, jreOsx) =>
			JreDependency(
				name = "jre 7u55", rawPath = "jre1.7.0_55", destPath = "jre",
				files = List(
					DependencyFile(Platform.Windows, jreWin, FileFormat.TarGz),
					DependencyFile(Platform.Linux, jreLin, FileFormat.TarGz),
					DependencyFile(Platform.OSX, jreOsx, FileFormat.TarGz)
				)
			)
		},

		nwkWindows in PackageDependencies := "http://dl.node-webkit.org/v0.9.2/node-webkit-v0.9.2-win-ia32.zip",
		nwkLinux in PackageDependencies := "http://dl.node-webkit.org/v0.9.2/node-webkit-v0.9.2-linux-ia32.tar.gz",
		nwkOsx in PackageDependencies := "http://dl.node-webkit.org/v0.9.2/node-webkit-v0.9.2-osx-ia32.zip",
		dependencyList in PackageDependencies <+= (nwkWindows in PackageDependencies, nwkLinux in PackageDependencies, nwkOsx in PackageDependencies) map { (nwkWin, nwkLin, nwkOsx) =>
			new PlatformDependency(
				name = "node-webkit v0.9.2", destPath = "node-webkit",
				files = List(
					DependencyFile(Platform.Windows, nwkWin, FileFormat.Zip),
					DependencyFile(Platform.Linux, nwkLin, FileFormat.TarGz),
					DependencyFile(Platform.OSX, nwkOsx, FileFormat.Zip)
				)
			) with PackageHelper {
				private val trimPathRegex = ("^\\Qnode-webkit-v0.9.2-linux-ia32\\E/").r
				def trimPath(platform: Platform)(path: String) = platform match {
					case Platform.Linux => trimPathRegex.replaceFirstIn(path, "")
					case _ => path
				}
			}
		},

		jetty in PackageDependencies := "http://mirrors.xmission.com/eclipse/jetty/9.1.4.v20140401/dist/jetty-distribution-9.1.4.v20140401.zip",
		//resourcer in PackageDependencies := "http://anolis.codeplex.com/downloads/get/81545",
		resourcer in PackageDependencies := "https://dl.dropboxusercontent.com/s/zifogi9efgtsq1s/Anolis.Resourcer-0.9.zip?dl=1",
		dependencyList in PackageDependencies <++= (jetty in PackageDependencies, resourcer in PackageDependencies) map { (jetty, resourcer) =>
			val jettyDep = new CommonDependency("Jetty 9.1.4 v20140401", "jetty", DependencyFile(Platform.Unspecified, jetty, FileFormat.Zip)) with PackageHelper {
				private val trimPathRegex = ("^\\Qjetty-distribution-9.1.4.v20140401\\E/").r
				def trimPath(platform: Platform)(path: String) = {
					trimPathRegex.replaceFirstIn(path, "")
				}
			}

			val resourcerDep = ToolDependency("Resourcer", "resourcer", DependencyFile(Platform.Unspecified, resourcer, FileFormat.Zip))

			jettyDep :: resourcerDep :: Nil
		},

		fetchDependencies <<= fetchDependenciesTask
	)
}