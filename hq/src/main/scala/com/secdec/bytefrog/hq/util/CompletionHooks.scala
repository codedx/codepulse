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

/** Mixin utility that adds a "completion" field to an object.
  * Completion is a [[scala.concurrent.Future]].
  */
trait CompletionHooks {
	private val completionPromise = Promise[Unit]

	/** Trigger completion. After calling this method, `isComplete` will be `true`.
	  * The first time `complete` is called, any handlers attached to the `completion`
	  * Future, or attached by `onComplete` will be triggered.
	  */
	protected def complete = completionPromise trySuccess ()

	/** @return whether this object is "completed" */
	def isComplete = completionPromise.isCompleted

	/** A future representing the "completion" of this object */
	def completion = completionPromise.future

	/** Attach a block of code to be run the first time `complete` is called. */
	def onComplete(body: => Unit)(implicit ctx: ExecutionContext): Unit = {
		completion onSuccess { case _ => body }
	}

}