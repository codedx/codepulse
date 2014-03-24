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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Adapter for instrumenting methods within a class with trace calls.
 * @author RobertF
 */
public class TraceClassAdapter extends ClassVisitor implements Opcodes
{
	private final String className;

	/**
	 * Constructor
	 * @param cv the class visitor to delegate to
	 * @param className the name of the class being instrumented
	 */
	public TraceClassAdapter(final ClassVisitor cv, String className)
	{
		super(ASM4, cv);

		this.className = className;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature,
			String[] exceptions)
	{
		MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
		return mv == null ? null : new TraceMethodAdapter(className, name, desc, access, mv);
	}
}
