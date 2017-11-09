package com.codedx.bytefrog.instrumentation;

import com.codedx.bytefrog.instrumentation.handler.TraceHandler;

import com.codedx.bytefrog.thirdparty.asm.Label;
import com.codedx.bytefrog.thirdparty.asm.Handle;
import com.codedx.bytefrog.thirdparty.asm.MethodVisitor;
import com.codedx.bytefrog.thirdparty.asm.Opcodes;
import com.codedx.bytefrog.thirdparty.asm.Type;
import com.codedx.bytefrog.thirdparty.asm.commons.AdviceAdapter;

/** Adapter for instrumenting methods with entry/exit hooks and line-level tracing.
  *
  * @author robertf
  */
class MethodInstrumentor extends AdviceAdapter {
	private final ClassInstrumentor ci;
	private final int access;
	private final String desc;
	private final int methodId;
	private final MethodInspector.Result inspection;

	private final TraceHandler handler;

	private final Type bitSetType = Type.getType(java.util.BitSet.class);
	private final Type throwableType = Type.getType(Throwable.class);

	boolean isPendingLineTrace = false;
	int currentLine = 0;

	public MethodInstrumentor(final ClassInstrumentor ci, final MethodVisitor mv, final int access, final String methodName, final String desc, final int methodId, final MethodInspector.Result inspection, final TraceHandler handler) {
		super(Opcodes.ASM5, mv, access, methodName, desc);

		this.ci = ci;
		this.access = access;
		this.desc = desc;
		this.methodId = methodId;
		this.inspection = inspection;

		this.handler = handler;
	}

	@Override protected void onMethodEnter() {
		initializeLineLevelInstrumentation();
		openTryCatchWrap();
		instrumentEntry();
		super.onMethodEnter();
	}

	@Override public void visitLineNumber(int line, Label label) {
		super.visitLineNumber(line, label);
		currentLine = line;
		isPendingLineTrace = true;
	}

	@Override public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		instrumentLine();
		super.visitFieldInsn(opcode, owner, name, desc);
	}

	@Override public void visitInsn(int opcode) {
		instrumentLine();
		super.visitInsn(opcode);
	}

	@Override public void visitIntInsn(int opcode, int operand) {
		instrumentLine();
		super.visitIntInsn(opcode, operand);
	}

	@Override public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
		instrumentLine();
		super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
	}

	@Override public void visitJumpInsn(int opcode, Label label) {
		instrumentLine();
		super.visitJumpInsn(opcode, label);
	}

	@Override public void visitLdcInsn(Object cst) {
		instrumentLine();
		super.visitLdcInsn(cst);
	}

	@Override public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
		instrumentLine();
		super.visitLookupSwitchInsn(dflt, keys, labels);
	}

	@Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
		instrumentLine();
		super.visitMethodInsn(opcode, owner, name, desc, itf);
	}

	@Override public void visitMultiANewArrayInsn(String desc, int dims) {
		instrumentLine();
		super.visitMultiANewArrayInsn(desc, dims);
	}

	@Override public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
		instrumentLine();
		super.visitTableSwitchInsn(min, max, dflt, labels);
	}

	@Override public void visitTypeInsn(int opcode, String type) {
		instrumentLine();
		super.visitTypeInsn(opcode, type);
	}

	@Override public void visitVarInsn(int opcode, int var) {
		instrumentLine();
		super.visitVarInsn(opcode, var);
	}

	@Override public void visitIincInsn(int var, int increment) {
		instrumentLine();
		super.visitIincInsn(var, increment);
	}

	@Override protected void onMethodExit(int opcode) {
		super.onMethodExit(opcode);

		// if we're exiting via a throw, our try/catch will handle it
		if (opcode != Opcodes.ATHROW) {
			instrumentExit(false);
		}
	}

	@Override public void visitMaxs(int maxStack, int maxLocals) {
		// visitMaxs is called after the code is complete, so we close our top level try/catch block here
		closeTryCatchWrap();

		// COMPUTE_MAXS will take care of figuring out max stack/locals
		super.visitMaxs(0, 0);
	}


	private boolean trackingLines = false;
	private int lineMapVar;

	private final Label methodBegin = new Label(), methodEnd = new Label();

	/** instrumentation to initialize line-level tracing */
	private void initializeLineLevelInstrumentation() {
		// set up line-level tracing, if we have the proper information
		if (inspection != null && inspection.hasLineInformation()) {
			// lineMap = new BitSet(endLine - startLine + 1)
			lineMapVar = newLocal(bitSetType);
			mv.visitTypeInsn(Opcodes.NEW, bitSetType.getInternalName());
			mv.visitInsn(Opcodes.DUP);
			BytecodeUtil.pushInt(mv, inspection.getEndLine() - inspection.getStartLine() + 1);
			mv.visitMethodInsn(
				Opcodes.INVOKESPECIAL,
				bitSetType.getInternalName(),
				"<init>",
				Type.getMethodDescriptor(
					Type.VOID_TYPE,
					Type.INT_TYPE
				),
				false
			);
			mv.visitVarInsn(Opcodes.ASTORE, lineMapVar);
			trackingLines = true;
		}
	}

	/** instrumentation to observe exceptions bubbling out of the method */
	private void openTryCatchWrap() {
		// insert start label for the try block
		mv.visitLabel(methodBegin);
	}

	/** instrumentation to observe exceptions bubbling out of the method */
	private void closeTryCatchWrap() {
		// wire up our try/catch around the whole method, so we can observe exception bubbling
		mv.visitTryCatchBlock(methodBegin, methodEnd, methodEnd, null);
		mv.visitLabel(methodEnd);

		instrumentExit(true);

		// rethrow the exception we caught
		mv.visitInsn(Opcodes.ATHROW);
	}

	/** build a frame for the top-level try/catch exception handler */
	private void buildCatchFrame() {
		Type[] argumentTypes = Type.getArgumentTypes(desc);
		Object[] locals = new Object[argumentTypes.length + 2]; // at most we have two more locals than the arguments
		int l = 0;

		if ((access & Opcodes.ACC_STATIC) == 0) {
			// not static, so add our own type
			locals[l++] = ci.getName();
		}

		// add the argument types
		for (Type argument : argumentTypes) {
			switch (argument.getSort()) {
				case Type.BOOLEAN:
				case Type.CHAR:
				case Type.BYTE:
				case Type.SHORT:
				case Type.INT:
					locals[l++] = Opcodes.INTEGER;
					break;

				case Type.FLOAT:
					locals[l++] = Opcodes.FLOAT;
					break;

				case Type.LONG:
					locals[l++] = Opcodes.LONG;
					break;

				case Type.DOUBLE:
					locals[l++] = Opcodes.DOUBLE;
					break;

				case Type.ARRAY:
				case Type.OBJECT:
					locals[l++] = argument.getInternalName();
					break;
			}
		}

		if (trackingLines) {
			// add our local bitset
			locals[l++] = bitSetType.getInternalName();
		}

		// new frame with our calculated locals, and a stack with throwable
		mv.visitFrame(
			Opcodes.F_NEW,
			l, locals,
			1, new Object[] { throwableType.getInternalName() }
		);
	}

	/** instrumentation to track method entries */
	private void instrumentEntry() {
		handler.instrumentEntry(mv, methodId, inspection);
	}

	/** instrumentation to track method exits */
	private void instrumentExit(boolean thrownException) {
		if (thrownException) {
			// when catching an exception, we need a full frame for the handler
			buildCatchFrame();
		}

		handler.instrumentExit(mv, methodId, inspection, thrownException);
		if (trackingLines) handler.instrumentLineCoverage(mv, methodId, inspection, lineMapVar);
	}

	/** instrumentation to track line-level execution */
	private void instrumentLine() {
		if (trackingLines && isPendingLineTrace) {
			// `lineMap`.set(line - startLine)
			mv.visitVarInsn(Opcodes.ALOAD, lineMapVar);
			BytecodeUtil.pushInt(mv, currentLine - inspection.getStartLine());
			mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				bitSetType.getInternalName(),
				"set",
				Type.getMethodDescriptor(
					Type.VOID_TYPE,
					Type.INT_TYPE
				),
				false
			);
		}

		isPendingLineTrace = false;
	}
}