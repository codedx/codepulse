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

package com.codedx.codepulse.hq.trace

import scala.collection.mutable.ArrayBuffer

sealed trait TracePlayer

sealed trait StopMethod
object StopMethod {
	/** A graceful stop has been requested - e.g., finish doing whatever you're doing/keep all data  */
	case object GracefulStop extends StopMethod

	/** An immediate halt has been requested - e.g., immediately cease processing/throw away data */
	case object ImmediateHalt extends StopMethod
}

/** Trait for trace players who implement stop control methods */
trait StopControl extends TracePlayer {
	def stop(how: StopMethod)
}

/** Trait for trace players who require cleanup at trace end */
trait Cleanup extends TracePlayer {
	def cleanup()
}

/** TracePlayerManager manages the lifetime of the moving parts contained in a trace. It provides
  * tracking of components and forwarding lifetime events to supporting players.
  *
  * @author robertf
  */
class TracePlayerManager {
	private val players = ArrayBuffer[TracePlayer]()

	def +=(p: TracePlayer) = players += p
	def ++=(p: TraversableOnce[TracePlayer]) = players ++= p

	def -=(p: TracePlayer) = players -= p
	def --=(p: TraversableOnce[TracePlayer]) = players --= p

	def stop(how: StopMethod) {
		for (player <- players) player match {
			case p: StopControl => p.stop(how)
			case _ =>
		}
	}

	def cleanup() {
		for (player <- players) player match {
			case p: Cleanup => p.cleanup
			case _ =>
		}
	}
}