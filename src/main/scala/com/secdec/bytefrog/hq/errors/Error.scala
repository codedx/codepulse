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

package com.secdec.bytefrog.hq.errors

sealed trait Error {
	def errorMessage: String
	def exception: Option[Throwable]
}

/** An unexpected error */
case class UnexpectedError(val errorMessage: String, val exception: Option[Throwable] = None) extends Error

object UnexpectedError {
	def apply(msg: String, e: Throwable): UnexpectedError = UnexpectedError(msg, Some(e))
}

/** An error that is overlooked after receiving shutdown message from Agent */
case class ConditionalError(val errorMessage: String, val exception: Option[Throwable] = None) extends Error

object ConditionalError {
	def apply(msg: String, e: Throwable): ConditionalError = ConditionalError(msg, Some(e))
}