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

/** Utility class for easy pluralization of strings.
  * @param num The number of (whatever) to be displayed as a string
  */
class Pluralizer(num: Long) {
	def plural = num != 1

	/** Displays "`num` `subj`"
	  * @example `1("thing")` displays `1 thing`
	  */
	def apply(subj: String) = num + " " + subj

	/** Appends an "s" to `subj` if `num` is plural
	  */
	def s(subj: String) = apply(if (plural) subj + 's' else subj)

	/** Appends an "es" to `subj` if `num` is plural
	  */
	def es(subj: String) = apply(if (plural) subj + "es" else subj)

	/** Appends "ies" to all but the last character of `subj` if `num` is plural.
	  * Useful for words that end in "y"
	  */
	def ies(subj: String) = apply(if (plural) subj.dropRight(1) + "ies" else subj)
}

class StringPluralizer(str: String) {
	def plural(count: Long): Boolean = count != 1

	private def plural(suffix: String, count: Long): String =
		if (plural(count)) suffix else ""

	def s(count: Long) = str + plural("s", count)
	def es(count: Long) = str + plural("es", count)
	def ies(count: Long) = if (plural(count)) str.dropRight(1) + "ies" else str
}