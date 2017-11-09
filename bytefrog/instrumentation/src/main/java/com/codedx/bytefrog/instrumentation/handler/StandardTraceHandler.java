package com.codedx.bytefrog.instrumentation.handler;

import com.codedx.bytefrog.instrumentation.BytecodeUtil;
import com.codedx.bytefrog.instrumentation.MethodInspector;

import com.codedx.bytefrog.thirdparty.asm.MethodVisitor;
import com.codedx.bytefrog.thirdparty.asm.Opcodes;
import com.codedx.bytefrog.thirdparty.asm.Type;

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