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

import scala.concurrent.ExecutionContext
import scala.concurrent.Promise

trait Completable[R] {
	private val completionPromise = Promise[R]
	protected def complete(result: R) = completionPromise trySuccess result

	/** @return whether this object is completed
	  */
	def isComplete = completionPromise.isCompleted

	/** A future representing the "completion" result of this object
	  */
	def completion = completionPromise.future

	/** Register a callback function that will be called when this object completes.
	  *
	  * @param body The callback function
	  */
	def onComplete(body: R => Unit)(implicit ctx: ExecutionContext): Unit = {
		completion onSuccess { case r => body(r) }
	}
}