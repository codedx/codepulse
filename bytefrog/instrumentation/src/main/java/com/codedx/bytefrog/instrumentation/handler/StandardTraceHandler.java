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

package com.codedx.bytefrog.instrumentation.handler;

import com.codedx.bytefrog.instrumentation.BytecodeUtil;
import com.codedx.bytefrog.instrumentation.MethodInspector;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** A basic TraceHandler adapter for injecting calls to implementations of a standard trace
  * collector interface.
  *
  * The provided trace collector type shall statically implement the following methods:
  * 	* void methodEnter(int methodId)
  * 	* void methodExit(int methodId, boolean exceptionThrown)
  * 	* void recordLineLevelTrace(int methodId, int startLine, int endLine, java.util.BitSet lineMap)
  *
  * @author robertf
  */
public class StandardTraceHandler implements TraceHandler {
	private final Type bitSetType = Type.getType(java.util.BitSet.class);
	private final Type traceCollectorType;

	public StandardTraceHandler(Type traceCollectorType) {
		this.traceCollectorType = traceCollectorType;
	}

	public void instrumentEntry(final MethodVisitor mv, final int methodId, final MethodInspector.Result method) {
		BytecodeUtil.pushInt(mv, methodId);
		mv.visitMethodInsn(
			Opcodes.INVOKESTATIC,
			traceCollectorType.getInternalName(),
			"methodEnter",
			Type.getMethodDescriptor(
				Type.VOID_TYPE,
				Type.INT_TYPE
			),
			false
		);
	}

	public void instrumentExit(final MethodVisitor mv, final int methodId, final MethodInspector.Result method, final boolean exceptionThrown) {
		BytecodeUtil.pushInt(mv, methodId);
		mv.visitIntInsn(Opcodes.BIPUSH, exceptionThrown ? 1 : 0);
		mv.visitMethodInsn(
			Opcodes.INVOKESTATIC,
			traceCollectorType.getInternalName(),
			"methodExit",
			Type.getMethodDescriptor(
				Type.VOID_TYPE,
				Type.INT_TYPE,
				Type.BOOLEAN_TYPE
			),
			false
		);
	}

	public void instrumentLineCoverage(final MethodVisitor mv, final int methodId, final MethodInspector.Result method, final int lineMapVar) {
		BytecodeUtil.pushInt(mv, methodId);
		mv.visitLdcInsn(method.getStartLine());
		mv.visitLdcInsn(method.getEndLine());
		mv.visitVarInsn(Opcodes.ALOAD, lineMapVar);
		mv.visitMethodInsn(
			Opcodes.INVOKESTATIC,
			traceCollectorType.getInternalName(),
			"recordLineLevelTrace",
			Type.getMethodDescriptor(
				Type.VOID_TYPE,
				Type.INT_TYPE,
				Type.INT_TYPE,
				Type.INT_TYPE,
				bitSetType
			),
			false
		);
	}
}