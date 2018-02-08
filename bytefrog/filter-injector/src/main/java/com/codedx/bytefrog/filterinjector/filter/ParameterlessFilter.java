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

import com.codedx.bytefrog.util.ClassLoaderUtil;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** An InjectableFilter adapter for filters with a parameterless constructor.
  *
  * @author robertf
  */
public class ParameterlessFilter implements InjectableFilter {
	private final Type filterType;
	private final String name, displayName;

	public String getName() { return name; }
	public String getDisplayName() { return displayName; }

	public ParameterlessFilter(Type filterType, String name, String displayName) {
		this.filterType = filterType;
		this.name = name;
		this.displayName = displayName;
	}

	public boolean isAvailable(final ClassLoader cl) {
		return ClassLoaderUtil.injectClass(cl, filterType);
	}

	public void constructFilter(final MethodVisitor mv) {
		// new <filterType>()
		mv.visitTypeInsn(Opcodes.NEW, filterType.getInternalName());
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, filterType.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE), false);
	}
}