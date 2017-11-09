package com.codedx.bytefrog.instrumentation.handler;

import com.codedx.bytefrog.instrumentation.MethodInspector;
import com.codedx.bytefrog.thirdparty.asm.MethodVisitor;

/** TraceHandler is an adapter for building bytecode to inject trace handler calls.
  *
  * @author robertf
  */
public interface TraceHandler {
	/** Injects method entry instrumentation for the provided method ID.
	  * @param mv the methodVisitor to build the filter in
	  * @param methodId the id (from `MethodIdentifier`) of the method being entered
	  * @param method the method inspector result of the method being entered
	  */
	void instrumentEntry(final MethodVisitor mv, final int methodId, final MethodInspector.Result method);

	/** Injects method exit instrumentation for the provided method ID.
	  * @param mv the methodVisitor to build the filter in
	  * @param methodId the id (from `MethodIdentifier`) of the method being exited
	  * @param method the method inspector result of the method being exited
	  * @param exceptionThrown whether or not the method exit is due to a thrown exception
	  */
	void instrumentExit(final MethodVisitor mv, final int methodId, final MethodInspector.Result method, final boolean exceptionThrown);

	/** Injects method line level reporting for the provided method ID.
	  * @param mv the methodVisitor to build the filter in
	  * @param methodId the id (from `MethodIdentifier`) of the method
	  * @param method the method inspector result of the method
	  * @param lineMapVar the line map variable ID: corresponds to a `java.util.BitSet` (that can
	  * 	be loaded with `mv.visitVarInsn(Opcodes.ALOAD, lineMapVar)`) whose bits correspond to
	  * 	if each line of the method was hit, index 0 corresponding with `method.getStartLine()`
	  */
	void instrumentLineCoverage(final MethodVisitor mv, final int methodId, final MethodInspector.Result method, final int lineMapVar);
}