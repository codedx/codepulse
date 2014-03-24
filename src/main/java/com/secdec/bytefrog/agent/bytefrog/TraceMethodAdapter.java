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

package com.secdec.bytefrog.agent.bytefrog;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

/**
 * Adapter for instrumenting methods with trace calls.
 * 
 * @author RobertF
 */
public class TraceMethodAdapter extends AdviceAdapter implements Opcodes
{
	private final String methodSignature;
	private int currentLineNumber = -1;

	private final Label methodBegin = new Label();
	private final Label methodEnd = new Label();
	private final Label catchBlock = new Label();

	/**
	 * Constructor
	 * @param className the name of the class the method belongs to
	 * @param methodName the name of the method being instrumented
	 * @param desc the method's descriptor
	 * @param access the method's access flags
	 * @param mv the method visitor to delegate to
	 */
	public TraceMethodAdapter(String className, String methodName, String desc, int access,
			MethodVisitor mv)
	{
		super(ASM4, mv, access, methodName, desc);
		this.methodSignature = className + "." + methodName + ";" + access + ";" + desc;
	}

	@Override
	protected void onMethodEnter()
	{
		// insert start label for the try block
		mv.visitLabel(methodBegin);

		// call Trace.methodEntry(methodSignature)
		mv.visitLdcInsn(methodSignature);
		mv.visitMethodInsn(INVOKESTATIC, "com/secdec/bytefrog/agent/trace/Trace", "methodEntry",
				"(Ljava/lang/String;)V");
	}

	@Override
	protected void onMethodExit(int opcode)
	{
		if (opcode == ATHROW)
		{
			// call Trace.methodThrow(exception, methodSignature,
			// currentLineNumber)
			dup(); // exception is already on stack, dup it since it's not ours
			mv.visitLdcInsn(methodSignature);
			mv.visitIntInsn(SIPUSH, currentLineNumber);
			mv.visitMethodInsn(INVOKESTATIC, "com/secdec/bytefrog/agent/trace/Trace", "methodThrow",
					"(Ljava/lang/Throwable;Ljava/lang/String;I)V");
		}
		else
		{
			// call Trace.methodExit(methodSignature, currentLineNumber)
			mv.visitLdcInsn(methodSignature);
			mv.visitIntInsn(SIPUSH, currentLineNumber);
			mv.visitMethodInsn(INVOKESTATIC, "com/secdec/bytefrog/agent/trace/Trace", "methodExit",
					"(Ljava/lang/String;I)V");
		}
	}

	@Override
	public void visitLineNumber(int line, Label label)
	{
		super.visitLineNumber(line, label);

		currentLineNumber = line;
	}

	@Override
	public void visitMaxs(int maxStack, int maxLocals)
	{
		// insert end label for the try block
		mv.visitLabel(methodEnd);

		// setup the try catch block
		mv.visitTryCatchBlock(methodBegin, methodEnd, catchBlock, null);

		// top level exception handler; this lets us see all exceptions and
		// detect when they bubble... make sure to re-throw the exception at
		// the end though
		mv.visitLabel(catchBlock);

		// stackmap frame signaling that just the exception thrown has been
		// added to the stack
		mv.visitFrame(F_NEW, 0, null, 1, new Object[] { "java/lang/Throwable" });

		// call Trace.methodBubble(exception, methodSignature)
		dup(); // exception is already on stack, dup it so we can rethrow later
		mv.visitLdcInsn(methodSignature);
		mv.visitMethodInsn(INVOKESTATIC, "com/secdec/bytefrog/agent/trace/Trace", "methodBubble",
				"(Ljava/lang/Throwable;Ljava/lang/String;)V");

		// re-throw the exception
		mv.visitInsn(ATHROW);

		// we require more stack space, but COMPUTE_MAXS take care of figuring
		// out the proper values here (maxStack and maxLocals are ignored at
		// this point).
		super.visitMaxs(0, 0);
	}
}
