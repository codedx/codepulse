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

package com.codedx.bytefrog.instrumentation;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Some basic bytecode helper utilities
  *
  * @author robertf
  */
public class BytecodeUtil {
	// static
	private BytecodeUtil() {}

	/** Push an integer if it's small enough, otherwise, load it as a constant on the stack.
	  * @param mv the method visitor
	  * @param value the integer value
	  */
	public static void pushInt(final MethodVisitor mv, final int value) {
		if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE)
			mv.visitIntInsn(Opcodes.BIPUSH, value);
		else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE)
			mv.visitIntInsn(Opcodes.SIPUSH, value);
		else
			mv.visitLdcInsn(new Integer(value));
	}
}