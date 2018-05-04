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

package com.secdec.codepulse.util

import language.implicitConversions
import scala.util.{ Try, Failure => TryFailure, Success => TrySuccess }

import net.liftweb.common.{ Box, Empty => BoxEmpty, Failure => BoxFailure, Full => BoxFull }
import scala.util.matching.Regex
import java.io.Closeable

import com.avi.codedx.util.RichFile

object Implicits {
	implicit def intToPluralizer(i: Int) = new Pluralizer(i)
	implicit def longToPluralizer(l: Long) = new Pluralizer(l)
	implicit def stringToPluralizer(str: String) = new StringPluralizer(str)
	implicit def pimpFile(file: java.io.File) = new RichFile(file)

	/** Adds the `nonEmptyList` method to Lists */
	implicit class NonEmptyList[A](list: List[A]) {
		def nonEmptyList = list match {
			case Nil => None
			case full => Some(full)
		}
	}

	implicit class NonEmptyString(s: String) {
		def nonEmptyString = if (s.isEmpty) None else Some(s)
	}

	/** Adds `toBox` to convert `Try[T]` to a `Box[T]` */
	implicit class TryBoxConverter[T](tri: Try[T]) {
		def toBox = tri match {
			case TrySuccess(r) => BoxFull(r)
			case TryFailure(e) => new BoxFailure(e.getMessage, BoxFull(e), BoxEmpty)
		}
	}

	/** Adds `tryGet` to convert `Option[T]` to a `Try[T]` */
	implicit class OptionTryConverter[T](opt: Option[T]) {
		def tryGet(orElse: String) = opt match {
			case Some(r) => TrySuccess(r)
			case None => TryFailure(new NoSuchElementException(orElse))
		}
	}

	/** Adds `toTry` to convert `Box[T]` to a `Try[T]` */
	implicit class BoxTryConverter[T](box: Box[T]) {
		def toTry = box match {
			case BoxFull(r) => TrySuccess(r)
			case BoxFailure(msg, cause, _) =>
				if (cause.isDefined) TryFailure(cause.get)
				else TryFailure(new Exception(msg))
			case BoxEmpty => TryFailure(new NoSuchElementException)
		}
	}

	/** Add `matches` for regexes to String */
	implicit class RegexMatcherString(str: String) {
		def matches(r: Regex) = r.matches(str)
	}

	/** Add `matches` to Regex */
	implicit class RichRegex(r: Regex) {
		def matches(str: String) = r.pattern.matcher(str).matches
	}

	implicit class RichCloseable[T <: Closeable](it: T) {
		def closeAfter[R](body: T => R) = try {
			body(it)
		} finally {
			it.close
		}
	}

	/** Adds `maxesBy` to Lists */
	implicit class ListWithMaximums[A](list: List[A]) {

		/** Finds a list of the elements in this list which have the maximum
		  * value according to `byFunc`. Differs from `maxBy` in that it may
		  * return multiple values, and it will not fail for an empty list.
		  */
		def maxesBy[B: Ordering](byFunc: (A) => B): List[A] = {
			val ord = implicitly[Ordering[B]]
			import ord._ // for > and < comparisons

			def recurse(max: B, accum: List[A], remain: List[A]): List[A] = remain match {
				case Nil => accum
				case head :: tail =>
					val x = byFunc(head)

					// new max: replace accum with the new maximum
					if (x > max) recurse(x, List(head), tail)

					// under max: continue with the existing maxes
					else if (x < max) recurse(max, accum, tail)

					// equal to max: add the head to accum
					else recurse(max, head :: accum, tail)
			}

			list match {
				case head :: tail => recurse(byFunc(head), List(head), tail)
				case Nil => Nil
			}
		}
	}
}