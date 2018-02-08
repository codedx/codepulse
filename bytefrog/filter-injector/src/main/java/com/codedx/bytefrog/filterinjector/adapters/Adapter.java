/* bytefrog: a tracing instrumentation toolset for the JVM. For more information, see
 * <https://github.com/codedx/bytefrog>
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

package com.codedx.bytefrog.filterinjector.adapters;

import org.objectweb.asm.ClassVisitor;

/** Adapters are used for injecting servlet filters into a container at runtime, in a generic/
  * extensible manner. Adapterss are enumerated in FilterInjector such that an instrumentation
  * process can easily determine an appropriate injection method based on what classes are loaded
  * at runtime.
  *
  * @author robertf
  */
public interface Adapter {
	/** Returns a ClassVisitor to handle instrumentation of the class as necessary.
    * @param classLoader the class loader being used to load the class
	  * @param cv the ClassVisitor to delegate to (e.g., the one that accepts the instrumented class)
	  */
	ClassVisitor getClassVisitor(final ClassLoader classLoader, final ClassVisitor cv);
}