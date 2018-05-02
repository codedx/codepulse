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

package com.secdec.codepulse.data

import scala.collection.mutable.ListBuffer
import java.lang.reflect.Modifier

/** Describes a method signature.
  *
  * @param name The name of the method, e.g. "doStuff" or "getThings".
  * @param containingClass The fully-qualified name of the Class which this
  * 	method belongs to. For example: "com.foo.bar.Baz". Generics will not
  * 	be included in this name.
  * @param modifiers An integer that represents the modifiers for this method.
  * 	See `java.lang.reflect.Modifier` for methods of interpreting this number.
  * @param params A list of parameters that needed to call this method. For
  * 	example, the method `public void foo(int i, String s)` would have a
  * 	`params` list of `List(Primitive("Int"), ReferenceType("java.lang.String"))`.
  * @param returnType The type that this method returns. Void methods will return
  * 	`Primitive("Void")`; and other return types will follow the same convention
  * 	as those in the `params` list.
  * @param surrogateFor The MethodSignature for which this method signature is
  *                     a surrogate. For example, the implementation of a C# method
  *                     using the yield keyword in a method with the signature
  *                     "IEnumerable<int> Power(int number, int exponent)"
  *                     would instantiate and return a nested type named
  *                     "<Power>d__1" whose "bool IEnumerator.MoveNext()" method would
  *                     be a surrogate for the "Power" method. Another example, would
  *                     be use of the C# async keyword.
  */
case class MethodSignature(
	name: String,
	containingClass: String,
	file: Option[String],
	modifiers: Int,
	params: List[MethodTypeParam],
	returnType: MethodTypeParam,
	var surrogateFor: Option[MethodSignature] = None) {

	def isSurrogate: Boolean = return surrogateFor != None

	override def toString = "%s %s %s.%s(%s)".format(Modifier.toString(modifiers), returnType, containingClass, name, params.mkString(", "))
}