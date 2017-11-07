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

package com.secdec.bytefrog.hq.protocol

/** Container for some IO-related data types.
  */
object IO {
	/** Base trait for an object that may be received from some data source.
	  * An Input can be one of `Data`, `EOF`, or `Error`.
	  */
	sealed trait Input[+A]

	/** Input class that represents some received data, of type `A` */
	case class Data[A](data: A) extends Input[A]

	/** Input class that denotes the end of a file */
	case object EOF extends Input[Nothing]

	/** Input class that denotes an error that occurred during processing */
	case class Error(cause: Throwable) extends Input[Nothing]
}