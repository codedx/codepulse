package com.codedx.bytefrog.instrumentation;

import com.codedx.bytefrog.instrumentation.id.MethodIdentifier;
import com.codedx.bytefrog.instrumentation.handler.TraceHandler;

import com.codedx.bytefrog.thirdparty.asm.ClassVisitor;
import com.codedx.bytefrog.thirdparty.asm.MethodVisitor;
import com.codedx.bytefrog.thirdparty.asm.Opcodes;

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
		super(Opcodes.ASM5, cv);
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