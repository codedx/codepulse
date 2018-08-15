/*
 * Copyright 2018 Secure Decisions, a division of Applied Visions, Inc.
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
 *
 * This material is based on research sponsored by the Department of Homeland
 * Security (DHS) Science and Technology Directorate, Cyber Security Division
 * (DHS S&T/CSD) via contract number HHSP233201600058C.
 */
package com.secdec.codepulse.data.bytecode.parse

import java.net.{ MalformedURLException, URL }
import java.util.UUID
import java.util.concurrent.TimeUnit

import org.joda.time.format.ISODateTimeFormat
import scala.util.Try


/** Allow for case-insensitive string matching e.g.
  * {{{"HeLlO" match {
  *   case ci"HELLO" => "hi"
  * }
  * }}}
  * @param sc
  */
class CaseInsensitiveUnapply(val sc: StringContext) extends AnyVal {
	def unapply(other: String) = sc.parts.mkString.equalsIgnoreCase(other)
	def ci = this
}

object Implicits {
	/** Adds `ci` to `StringContext`, which lets you use case-insensitive unapply for matching */
	import scala.language.implicitConversions
	implicit def caseInsensitiveUnapply(sc: StringContext) = new CaseInsensitiveUnapply(sc)
}

/** Helpers for converting string values to numeric/boolean values.
  * Inspired by Lift's helpers.
  *
  * @author robertf
  */
object StringConverters extends StringConverters

trait StringConverters {
	private def tryConvert[T](in: String, conversion: String => T) =
		Option(in) flatMap { s => Try { conversion(s.trim) }.toOption }

	object AsBoolean {
		def unapply(str: String) = {
			import com.secdec.codepulse.data.bytecode.parse.Implicits.caseInsensitiveUnapply
			Option(str) match {
				case Some(ci"t" | ci"true"  | ci"yes" | "1" | ci"on")  => Some(true)
				case Some(ci"f" | ci"false" | ci"no"  | "0" | ci"off") => Some(false)
				case _ => None
			}
		}
	}

	object AsInt {
		def unapply(str: String) = tryConvert(str, _.toInt)
	}

	object AsLong {
		def unapply(str: String) = tryConvert(str, _.toLong)
	}

	object AsDouble {
		def unapply(str: String) = tryConvert(str, _.toDouble)
	}

	object AsTimeUnit {
		def unapply(str: String) = tryConvert(str, TimeUnit.valueOf)
	}

	object AsUUID {
		def unapply(str: String) = tryConvert(str, UUID.fromString)
	}

	object AsISODateTime {
		val format = ISODateTimeFormat.dateTime
		def unapply(str: String) = tryConvert(str, format.parseDateTime)
	}

	object AsUrl {
		def unapply(str: String) = {
			try{
				Some(new URL(str))
			} catch {
				case _: MalformedURLException => None
			}
		}
	}

	def asBoolean(str: String) = AsBoolean.unapply(str)
	def asInt(str: String) = AsInt.unapply(str)
	def asLong(str: String) = AsLong.unapply(str)
	def asDouble(str: String) = AsDouble.unapply(str)
	def asTimeUnit(str: String) = AsTimeUnit.unapply(str)
	def asUUID(str: String) = AsUUID.unapply(str)
	def asISODateTime(str: String) = AsISODateTime.unapply(str)
	def asUrl(str: String) = AsUrl.unapply(str)
}
