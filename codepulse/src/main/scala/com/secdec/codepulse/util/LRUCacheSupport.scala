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

import com.googlecode.concurrentlinkedhashmap._

trait LRUCacheSupport {

	type Cache[K, V] = ConcurrentLinkedHashMap[K, V]

	def newCache[K, V](capacity: Int): Cache[K, V] =
		new ConcurrentLinkedHashMap.Builder[K, V]
			.maximumWeightedCapacity(capacity)
			.build

	implicit class PimpedCache[K, V](cache: Cache[K, V]) {

		/** Get the value for the given `key` from the cache, if it exists;
		  * if it doesn't, perform the `work` function, store its result, and return it.
		  */
		def getOrElse[V1](key: K)(work: => V1)(implicit c: Cacheable[V, V1]) = cache.get(key) match {
			// cache miss: do the work and store+return the result
			case null => {
				val v = work
				//cache.put(key, v)
				c.addInto(key, v, cache)
				v
			}

			// cache hit: simply return
			case v => c.extract(v)
		}

		/** Add and return the given `value`, storing it under the given `key` */
		def update[V1](key: K)(value: => V1)(implicit c: Cacheable[V, V1]) = {
			val v = value
			//cache.put(key, v)
			c.addInto(key, v, cache)
			v
		}

		def invalidate(key: K): Unit = {
			cache.remove(key)
		}
	}

	/** Evidence that a value of type `V1` can be put into a cache
	  * that accepts values of type `V`.
	  */
	trait Cacheable[V, V1] {
		/** Add (key -> value) into the cache */
		def addInto[K](key: K, value: V1, cache: Cache[K, V]): Unit

		/** Extract the stored value V as a V1 */
		def extract(stored: V): V1
	}

	// Caches with value type V can accept values of type V
	implicit def getIdentityCacheable[V] = new IdentityCacheable[V]
	class IdentityCacheable[V] extends Cacheable[V, V] {
		def addInto[K](key: K, value: V, cache: Cache[K, V]): Unit = {
			cache.put(key, value)
		}
		def extract(value: V) = value
	}

	// Caches with value type V can accept values of type Option[V]
	implicit def getOptionCacheable[V] = new OptionCacheable[V]
	class OptionCacheable[V] extends Cacheable[V, Option[V]] {
		def addInto[K](key: K, value: Option[V], cache: Cache[K, V]): Unit = {
			for (v <- value) cache.put(key, v)
		}
		def extract(value: V) = Some(value)
	}
}