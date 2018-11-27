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

import com.codedx.bytefrog.instrumentation.id.MethodIdentifier;
import com.codedx.bytefrog.instrumentation.handler.TraceHandler;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Adapter for instrumenting classes (delegates to MethodInstrumentor).
  *
  * @author robertf
  */
public class ClassInstrumentor extends ClassVisitor {
	private final int classId;
	private final MethodIdentifier methodIdentifier;
	private final ClassInspector.Result inspection;

	private final TraceHandler handler;

	private String name;

	public String getName() { return name; }

	public ClassInstrumentor(final ClassVisitor cv, final MethodIdentifier methodIdentifier, final int classId, final ClassInspector.Result inspection, final TraceHandler handler) {
		super(Opcodes.ASM7, cv);
		this.classId = classId;
		this.methodIdentifier = methodIdentifier;
		this.inspection = inspection;

		this.handler = handler;
	}

	@Override public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
		this.name = name;
	}

	@Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		final MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
		final MethodInspector.Result methodInspection = inspection.lookupMethod(name, desc);
		return mv == null ? null : new MethodInstrumentor(this, mv, access, name, desc, methodIdentifier.record(classId, access, name, desc, methodInspection != null ? methodInspection.getStartLine() : -1, methodInspection != null ? methodInspection.getEndLine() : -1), methodInspection, handler);
	}
}