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

package com.codedx.bytefrog.filterinjector.filter;

import org.objectweb.asm.MethodVisitor;

/** InjectableFilter is an adapter for building bytecode to construct a filter for injection.
  *
  * @author robertf
  */
public interface InjectableFilter {
	/** Gets a name for the filter, to be passed to the servlet container as necessary */
	String getName();

	/** Gets a display name for the filter, to be passed to the servlet container as necessary */
	String getDisplayName();

	/** Checks if this filter is usable with the given classloader. Some implementations may try to
	  * inject the filter code into the class loader to make it available.
	  * @param cl the classloader to check
	  */
	boolean isAvailable(final ClassLoader cl);

	/** Constructs an instance of the injectable filter, leaving its reference on the stack.
	  * @param mv the methodVisitor to build the filter in
	  */
	void constructFilter(final MethodVisitor mv);
}