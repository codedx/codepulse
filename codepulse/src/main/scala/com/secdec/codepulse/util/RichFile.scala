package com.avi.codedx.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.BufferedInputStream

import scala.io.Codec
import scala.io.Source
import scala.util.Try

/** Pimped File class.
  * @author dylanh
  */
class RichFile(val file: File) extends AnyVal {

	def /(path: String) = new File(file, path)

	/** @return a list of this file's children. Will be `Nil` if this file isn't a directory */
	def children: List[File] = file.listFiles match {
		case null => Nil
		case array => array.toList
	}

	/** @return this file's parent, optionally */
	def parent: Option[File] = Option(file.getParentFile)

	//	/** @return the canonical version of this file */
	//	def normalize = file.getCanonicalFile

	def pathSegments: List[String] = {
		def path(f: File): List[String] = new RichFile(f).parent match {
			case Some(p) => f.getName :: path(p)
			case None => f.getName :: Nil
		}
		path(file).reverse
	}

	//	/** Read the file by performing the `body` function on a stream of its contents.
	//	  * @param body A function that takes the contents of the file and returns some result
	//	  * @return an option containing the result of the `body` function, or `None` if
	//	  * something went wrong
	//	  */
	//	def read[A](body: InputStream => A): Option[A] = {
	//		if (!file.canRead) None
	//		else {
	//			val in = new FileInputStream(file)
	//			try {
	//				val result = body(in)
	//				Some(result)
	//			} finally {
	//				in.close
	//			}
	//		}
	//	}

	def read[A](body: InputStream => A): A = {
		val in = new FileInputStream(file)
		try {
			body(in)
		} finally {
			in.close
		}
	}

	def readBuffered[A](body: BufferedInputStream => A): A = read { in =>
		val buffered = new BufferedInputStream(in)
		try {
			body(buffered)
		} finally {
			buffered.close
		}
	}

	def readAsSource[A](body: Source => A)(implicit codec: Codec): A = {
		val source = Source.fromFile(file)
		try {
			body(source)
		} finally {
			source.close()
		}
	}

	//
	//	def readBuffered[A](body: BufferedInputStream => A): Option[A] = read { in =>
	//		val buffered = new BufferedInputStream(in)
	//		try {
	//			val result = body(buffered)
	//			result
	//		} finally {
	//			buffered.close
	//		}
	//	}
	//
	//	/** Read the file in chunks.
	//	  * @param chunkSize the size of the chunks to read
	//	  * @param body A function that takes the contents of the file in the form of an iterator and returns some result.
	//	  * The iterator's elements are a tuple of a byte buffer, and a count. The count is the number of bytes in the
	//	  * buffer that are valid. Note that the byte buffer is reused for each iteration.
	//	  * @return An `Option` containing the result of the `body` function, or `None` if something went wrong.
	//	  */
	//	def readChunked[A](chunkSize: Int)(body: Iterator[(Array[Byte], Int)] => A): Option[A] = read { in =>
	//		try {
	//			val buf = new Array[Byte](chunkSize)
	//			val itr = Iterator.continually {
	//				val count = in.read(buf, 0, chunkSize)
	//				(buf, count)
	//			}.takeWhile(_._2 != -1)
	//			body(itr)
	//		} finally {
	//			in.close
	//		}
	//	}

	/** Write to this file.
	  * @param body A function that takes an output stream and writes to it.
	  * @return `true` if everything completes normally, `false` otherwise.
	  */
	def write(body: OutputStream => Unit): Boolean = {
		if (file.exists && !file.canWrite) false
		else {
			val out = new FileOutputStream(file)
			try {
				body(out)
				true
			} finally {
				out.close
			}
		}
	}

	//	/** Copy the contents of this file into the `dest`ination file.
	//	  * @param dest The destination file
	//	  * @return `true` on successful completion, `false` on an error
	//	  */
	//	def copyTo(dest: File): Boolean = {
	//		readChunked(2048) { in =>
	//			new RichFile(dest).write { out =>
	//				for { (buf, count) <- in } out.write(buf, 0, count)
	//			}
	//		} match {
	//			case Some(true) => true
	//			case _ => false
	//		}
	//	}

	/** Write the contents of the `stream` into this file.
	  * @param stream the source
	  * @return `true` on success, `false` on an error
	  */
	def loadFrom(stream: InputStream) = write { out =>
		try {
			val buf = new Array[Byte](2048);
			val itr = Iterator.continually {
				val count = stream.read(buf, 0, 2048)
				(buf, count)
			}.takeWhile(_._2 != -1)
			for { (buf, count) <- itr } out.write(buf, 0, count)
		} finally {
			stream.close
		}
	}
}