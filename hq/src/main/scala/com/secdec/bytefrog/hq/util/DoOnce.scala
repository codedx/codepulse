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

package com.codedx.codepulse.hq.util

import java.util.concurrent.atomic.AtomicBoolean

/** A utility for wrapping existing functions so that they may be called at most one time.
  *
  * This is not a replacement for lazy vals; the idea is that a function may be called with
  * some parameters while not caring about the return value.
  *
  * Example Usage: {{{
  * val initializeEverything = DoOnce{(param: InitParam) =>
  *   actuallyDoInitialization(param)
  * }
  *
  * initializeEverything(param) // calls actuallyDoInitialization
  * initializeEverything(param) // no op
  * initializeEverything(param2) // no op, even with different params
  * }}}
  */
object DoOnce {

	def apply(func: Function0[_]): Function0[Unit] = {
		val done = new AtomicBoolean(false)

		() => if (done.compareAndSet(false, true)) {
			func()
		}
	}

	def apply[T1](func: Function1[T1, _]): Function1[T1, Unit] = {
		val done = new AtomicBoolean(false)

		(arg: T1) => if (done.compareAndSet(false, true)) {
			func(arg)
		}
	}
}