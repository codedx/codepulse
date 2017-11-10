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

import scala.ref.WeakReference

/** Utility Map class that holds values in WeakReferences, so that values may
  * be automatically removed from the map as they get garbage-collected.
  */
class WeakMap[A, B <: AnyRef] extends collection.mutable.Map[A, B] {

	private val underlying = collection.mutable.Map[A, WeakReference[B]]()

	def +=(kv: (A, B)) = {
		underlying += kv._1 -> WeakReference(kv._2)
		this
	}

	def -=(key: A) = {
		underlying -= key
		this
	}

	def get(key: A) = underlying get key match {
		case Some(WeakReference(v)) => Some(v)
		case Some(deadRef) =>
			underlying -= key
			None
		case None => None
	}

	def iterator = underlying.iterator collect {
		case (k, WeakReference(v)) => (k, v)
	}

}