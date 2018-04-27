package com.secdec.codepulse.util

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import scala.io.Codec
import scala.io.Source
import scala.util.Try
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import org.apache.commons.io.input.BOMInputStream
import org.mozilla.universalchardet.UniversalDetector
import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.BoundedInputStream
import scala.util.control.NonFatal
import com.secdec.codepulse.util.Implicits._

/** A file loader that tries to be smart and detect the correct charset to load a text file.
  *
  * Uses juniversalchardet (a Java port of the Mozilla charset detector code) under the hood.
  * See https://code.google.com/p/juniversalchardet/ for more details.
  *
  * @author robertf, anthonyd
  */
object SmartLoader {
	private val BufferSize = 4096
	val FallbackCodec = Codec.ISO8859

	sealed trait BoundedLoad
	case object TooLong extends BoundedLoad
	sealed trait UnboundedLoad extends BoundedLoad
	case class Success(content: String, codec: Codec) extends UnboundedLoad
	case class Failure(cause: Throwable) extends UnboundedLoad

	/** Detect the character set used for `stream`.
	  * *NOTE*: this consumes the stream.
	  *
	  * This may return None even on valid text files if a conclusive match wasn't found. In that
	  * case, try a sane default (e.g., ISO 8859)
	  */
	def detectCharset(stream: InputStream): Option[Codec] = {
		val detector = new UniversalDetector(null)

		val buffer = new Array[Byte](BufferSize)

		val chunks = Iterator.continually {
			stream.read(buffer, 0, buffer.length) -> buffer
		} takeWhile { _._1 > 0 }

		for ((len, buffer) <- chunks.takeWhile(_ => !detector.isDone)) {
			detector.handleData(buffer, 0, len)
		}

		detector.dataEnd

		Option(detector.getDetectedCharset).map(Codec(_))
	}

	/** Attempt to detect the proper codec for a file, then load the file with that codec, returning
	  * its contents as a string. If a working codec could not be identified for the file, None is
	  * returned.
	  */
	def loadFile(file: File): UnboundedLoad =
		try {
			val codecOpt = file.read(detectCharset)
			implicit val codec = codecOpt getOrElse FallbackCodec
			val content = file.readAsSource(_.mkString)
			Success(content, codec)
		} catch {
			case NonFatal(e) => Failure(e)
		}

	/** Same functionality as `loadFile(file)`, except that when the file is larger than the size limit,
	  * it will return `TooLong` and not attempt to load the file at all.
	  */
	def loadFile(file: File, sizeLimit: Long): BoundedLoad =
		if (file.length > sizeLimit) TooLong
		else loadFile(file)

	def loadBytes(bytes: Array[Byte]): UnboundedLoad =
		try {
			val codec = detectCharset(new ByteArrayInputStream(bytes)) getOrElse FallbackCodec
			val source = Source.fromInputStream(new BOMInputStream(new ByteArrayInputStream(bytes)))(codec)
			Success(source.mkString, codec)
		} catch {
			case NonFatal(e) => Failure(e)
		}

	def loadBytes(bytes: Array[Byte], sizeLimit: Long): BoundedLoad =
		if (bytes.length > sizeLimit) TooLong
		else loadBytes(bytes)

	/** Detect charset for `stream` and decode all contents of the stream,
	  * returning a string. This buffers the entire contents of the stream in
	  * memory.
	  */
	def loadStream(stream: InputStream): UnboundedLoad =
		try {
			val bytes = IOUtils.toByteArray(stream)
			loadBytes(bytes)
		} catch {
			case NonFatal(e) => Failure(e)
		}

	def loadStream(stream: InputStream, sizeLimit: Long) =
		try {
			val boundedStream = new BoundedInputStream(stream, sizeLimit)
			val bytes = IOUtils.toByteArray(boundedStream)
			val hasMore = stream.read != -1
			if (hasMore) TooLong
			else loadBytes(bytes)
		} catch {
			case NonFatal(e) => Failure(e)
		}
}