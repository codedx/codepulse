/*
 * bytefrog: a tracing framework for the JVM. For more information
 * see http://code-pulse.com/bytefrog
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

package com.secdec.bytefrog.hq.data.collection

import java.util.Comparator

import com.secdec.bytefrog.hq.protocol.DataMessage.SequencedData

/** A comparator that can sort sequenced data based on timestamp and sequence ID. There is special logic
  * at play to handle the fact that sequence ID can and will overflow at some point; after the overflow,
  * elements need to be placed after prior ones, although they now have a lower sequence ID.
  *
  * This comparator makes the assumption no more than half the range of possible sequence IDs are used
  * in one time unit. If this constraint is broken, proper sort order can no longer be guaranteed.
  *
  * @author robertf
  */
object DataOrdering extends Comparator[SequencedData] {
	val overflowLimit = (Int.MaxValue.toLong - Int.MinValue.toLong) / 2

	def compare(x1: SequencedData, x2: SequencedData): Int = {
		// Sequencing need not care about timestamp, since we're assuming it's impossible to have
		// ambiguous sequence IDs (range covers more than half the range of a 32-bit integer) in
		// a position to be compared. This assumption holds unless we're going to get a few *million*
		// messages per *millisecond*

		val sc = x1.sequence.compare(x2.sequence)

		// if an overflow has occurred between these two data points, invert the sequence comparison
		if (Math.abs(x1.sequence.toLong - x2.sequence.toLong) > overflowLimit)
			-sc
		else
			sc
	}
}