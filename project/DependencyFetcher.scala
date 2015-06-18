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
import Def.Initialize

import scala.collection.JavaConversions._
import scala.language.implicitConversions

import java.io.{ BufferedInputStream, InputStream, File, FileInputStream, FileOutputStream }
import java.net.{ HttpURLConnection, URL }
import java.util.Properties

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

/** Helper for fetching various dependencies from the web.
  * This object provides a DSL for creating SBT Tasks that download files from the web.
  * Example usage:
  * {{{
  * import DependencyFetcher._
  * val downloadMyFile = TaskKey[File]("downloadMyFile")
  *
  * downloadMyFile <<= Dependency("myfile", "v1.0.3", "http://my.coolfiles.com/myfile.txt")
  *   .downloadRaw
  *   .to { _ / "myfiles" / "myfile.txt" }
  * }}}
  *
  * Options are available to add a pre-connection side effect step (e.g. to set cookies on
  * the request), and for treating a downloaded file as a .zip or .tar.gz.
  *
  * @author robertf, dylanh
  */
object DependencyFetcher extends BuildExtra with SBinaryFormats {

	// type alias to effectively auto-import URLConnection when you import DependencyFetcher._
	type URLConnection = java.net.URLConnection

	/** Describes a file on the internet that we want to download.
	  * @param name The internal name to call the dependency.
	  * @param version The version of the file.
	  * @param url The location of the file
	  */
	case class Dependency(name: String, version: String, url: String) {

		/** Creates a DependencyConnector out of this Dependency with
		  * the given `preConnect` step.
		  */
		def withConnectionStep(preConnect: URLConnection => Unit) = DependencyConnector(this, preConnect)
	}

	/** Implicit upgrade from a Dependency to a DependencyConnector that
	  * uses a *no-op* `preConnect` step. This can be used to bypass the
	  * `withConnectionStep` call in the Dependency setup DSL.
	  */
	implicit def addNoopDepPreConnect(dep: Dependency): DependencyConnector = DependencyConnector(dep, _ => ())

	/** Intermediate class in the DependencyDownloader DSL. This class adds a
	  * 'preConnect' step that will be called as a connection to the `url`
	  * is established.
	  * @param dep a Dependency
	  * @param preConnect The side-effect step
	  */
	case class DependencyConnector(dep: Dependency, preConnect: URLConnection => Unit) {
		def downloadRaw = PreDepDownloader(this, RawDownloadHandler)
		def extractAsZip(fixPaths: String => String = identity) = PreDepDownloader(this, new ZipDownloadHandler(fixPaths))
		def extractAsTarGz(fixPaths: String => String = identity) = PreDepDownloader(this, new TarGzDownloadHandler(fixPaths))
	}

	/** An object that handles data downloaded from a stream, sending it
	  * (potentially after some transformation or manipulation) to the
	  * `destination` file or folder.
	  */
	trait DownloadHandler {
		def handleDownload(stream: InputStream, destination: File)
	}

	/** Intermediate class in the DependencyDownloader DSL. This class holds
	  * a DependencyConnector and a DownloadHandler. From here, just call the
	  * `to` method, supplying a `destination` in order to create the end
	  * result, which is an SBT `Task[File]`.
	  */
	case class PreDepDownloader(connector: DependencyConnector, handler: DownloadHandler) {

		/** Creates an SBT `Task` that will, when called, download the given dependency, using
		  * the given `connector` and `handler`, and funnel the data to the given `destination`.
		  * It will cache previous downloads to avoid duplicated effort, using the name+version+url
		  * of the dependency as the cache key.
		  *
		  * @param relativeDestination A function that takes a 'root' folder (which should be assumed
		  * to be the download cache root), and creates a new file path which will be used as the actual
		  * destination for the download.
		  */
		def to(relativeDestination: File => File): Initialize[Task[File]] = dependencyDownloaderTask(connector, handler, relativeDestination)
	}

	val BufferLen = 4096

	private class ProgressInputStream(underlying: InputStream, message: String, size: Long, reportInterval: Int) extends InputStream {
		private var bytesRead = 0L
		private var nextReport = 0
		private def progress(chunk: Int) {
			bytesRead += chunk
			val pct = 100 * bytesRead / size
			if (pct >= nextReport) {
				println(s"$message... $pct% complete")
				nextReport += reportInterval
			}
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

	private def doDownload(log: Logger, connector: DependencyConnector, handler: DownloadHandler, dest: File) {
		val DependencyConnector(dep, preConnect) = connector
		val label = s"${dep.name} v${dep.version}"

		if (dest.exists) {
			log info "Deleting " + dest + "..."
			if(dest.isDirectory) FileUtils deleteDirectory dest
			else dest.delete
		}

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

			connect(new URL(dep.url))
		}

		val stream = new BufferedInputStream(
			new ProgressInputStream(conn.getInputStream, "Downloading " + label, conn.getContentLengthLong, 25)
		)

		try {
			handler.handleDownload(stream, dest)
		} finally {
			IOUtils closeQuietly stream
		}

		// "Downloading <label>... xxx% complete" = 18 extra characters to clear
		log.info("Downloaded " + label + "                  ")
	}

	/** A DownloadHandler implementation that simply copies the contents of the
	  * download stream directly to the `destination` file.
	  */
	object RawDownloadHandler extends DownloadHandler {
		def handleDownload(stream: InputStream, destFile: File) {
			destFile.getParentFile.mkdirs

			val fos = new FileOutputStream(destFile)
			try {
				IOUtils.copyLarge(stream, fos)
			} finally {
				IOUtils closeQuietly fos
			}
		}
	}

	/** A DownloadHandler implementation that treats the download as a Zip file, extracting
	  * each entry out under the given `destination` folder after 'trimming' the entry path
	  * according to the `pathTrim` function.
	  */
	class ZipDownloadHandler(pathTrim: String => String) extends DownloadHandler {
		def handleDownload(stream: InputStream, destFolder: File) {
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
	}

	/** A DownloadHandler implementation that treats the download as a tar.gz file,
	  * expanding each entry out under the given `destination` folder after 'trimming'
	  * the entry path according to the `pathTrim` function.
	  */
	class TarGzDownloadHandler(pathTrim: String => String) extends DownloadHandler {
		def handleDownload(stream: InputStream, destFolder: File) {
		// private def processTarGz(stream: InputStream, destFolder: File, pathTrim: String => String) {
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
	}

	/*
	 * Dependency Cache File
	 */
	private val depCacheLock = new Object
	type DepMap = Map[String, (String, String)]
	private def checkDepCache(cacheFile: File, dep: Dependency): Boolean = depCacheLock.synchronized {
		val stored = CacheIO.fromFile[DepMap](cacheFile) getOrElse Map.empty
		stored get dep.name match {
			case Some((dep.version, dep.url)) => true
			case _ => false
		}
	}
	private def updateDepCache(cacheFile: File, dep: Dependency): Unit = depCacheLock.synchronized {
		val stored = CacheIO.fromFile[DepMap](cacheFile) getOrElse Map.empty
		val updated = stored + (dep.name -> (dep.version -> dep.url))
		CacheIO.toFile(updated)(cacheFile)
	}

	def dependencyDownloaderTask(connector: DependencyConnector, handler: DownloadHandler, relativeDestination: File => File) = Def.task[File] {
		val log = streams.value.log
		val downloadRoot = FetchCache.fetchCacheDir.value
		val cacheFile = downloadRoot / "dependencyFetcher.cache"
		val dest = relativeDestination(downloadRoot)
		val dep = connector.dep

		if(checkDepCache(cacheFile, dep)){
			log debug s"Dependency already up-to-date [${dep.name}]"
		} else {
			doDownload(log, connector, handler, dest)
			updateDepCache(cacheFile, dep)
		}

		dest
	}

}
