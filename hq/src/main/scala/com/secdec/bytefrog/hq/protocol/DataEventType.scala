/* Code Pulse: a real-time code coverage tool, for more information, see <http://code-pulse.com/>
 *
 * Copyright (C) 2014-2017 Code Dx, Inc. <https://codedx.com/>
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

package com.codedx.codepulse.hq.protocol

/** Signifies the <em>type</em> of a data event that can
  * be read by a [[DataEventReader]].
  *
  * Note: This trait is essentially an enumeration, and members
  * are not intended to hold any actual data
  * (as opposed to [[ControlMessage]]).
  */
sealed trait DataEventType

object DataEventType {
	case object MethodEntry extends DataEventType
	case object MethodVisit extends DataEventType
	case object MethodExit extends DataEventType
	case object ExceptionEvent extends DataEventType
	case object ExceptionBubbleEvent extends DataEventType
	case object MapMethodName extends DataEventType
	case object MapThreadName extends DataEventType
	case object Marker extends DataEventType
	case object MapSourceLocation extends DataEventType

	case object Unknown extends DataEventType
	case object EOF extends DataEventType
}