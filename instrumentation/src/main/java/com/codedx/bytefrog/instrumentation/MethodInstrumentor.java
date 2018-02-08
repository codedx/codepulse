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

import java.util.ArrayList;
import java.util.List;

import com.codedx.bytefrog.instrumentation.handler.TraceHandler;

import org.objectweb.asm.Label;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import com.esotericsoftware.minlog.Log;

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
	private final boolean isConstructor;

	private final TraceHandler handler;

	private final Type bitSetType = Type.getType(java.util.BitSet.class);
	private final Type throwableType = Type.getType(Throwable.class);

	private boolean hasEntered = false, canInstrument = true;
	private boolean isPendingLineTrace = false;
	private int currentLine = 0;

	// for keeping track of moved 'new' instructions (for uninitialized references in stackmap frames)
	private class NewLocation {
		public final Label original, replacement;

		public NewLocation(Label original, Label replacement) {
			this.original = original;
			this.replacement = replacement;
		}
	}
	private final List<NewLocation> newReplacementLocations = new ArrayList<>();

	public MethodInstrumentor(final ClassInstrumentor ci, final MethodVisitor mv, final int access, final String methodName, final String desc, final int methodId, final MethodInspector.Result inspection, final TraceHandler handler) {
		super(Opcodes.ASM5, mv, access, methodName, desc);

		this.ci = ci;
		this.access = access;
		this.desc = desc;
		this.methodId = methodId;
		this.inspection = inspection;
		isConstructor = methodName.equals("<init>");

		this.handler = handler;
	}

	@Override public void visitCode() {
		super.visitCode();

		if (isConstructor) {
			initializeLineLevelInstrumentation();
		}
	}

	@Override protected void onMethodEnter() {
		super.onMethodEnter();

		if (!isConstructor) {
			initializeLineLevelInstrumentation();
		}

		if (canInstrument && !hasEntered) {
			openTryCatchWrap();
			instrumentEntry();
			hasEntered = true;
		} else {
			if (Log.DEBUG) Log.debug("method instrumentation", String.format("cannot instrument method %s.%s:%s; skipping", ci.getName(), inspection.getName(), desc));
		}

		if (hasEntered && isPendingLineTrace) {
			instrumentLine();
			if (Log.DEBUG) Log.debug("method instrumentation", String.format("line level coverage for %s lines %d-%d potentially missing", inspection.getClassInspection().getFileName(), inspection.getStartLine(), currentLine));
		}
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

		// any jumping in constructors prior to super constructor call is branching, and too
		// complex for us to instrument currently
		if (isConstructor && !hasEntered) {
			canInstrument = false;
			if (Log.TRACE) Log.trace("method instrumentation", String.format("jump encountered in constructor %s.%s:%s prior to object initialization; unable to instrument", ci.getName(), inspection.getName(), desc));
		}
	}

	@Override public void visitLdcInsn(Object cst) {
		instrumentLine();
		super.visitLdcInsn(cst);
	}

	@Override public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
		instrumentLine();
		super.visitLookupSwitchInsn(dflt, keys, labels);

		// this is branching, too complex prior to super constructor call
		if (isConstructor && !hasEntered) {
			canInstrument = false;
			if (Log.TRACE) Log.trace("method instrumentation", String.format("lookup switch encountered in constructor %s.%s:%s prior to object initialization; unable to instrument", ci.getName(), inspection.getName(), desc));
		}
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

		// this is branching, too complex prior to super constructor call
		if (isConstructor && !hasEntered) {
			canInstrument = false;
			if (Log.TRACE) Log.trace("method instrumentation", String.format("table switch encountered in constructor %s.%s:%s prior to object initialization; unable to instrument", ci.getName(), inspection.getName(), desc));
		}
	}

	@Override public void visitTypeInsn(int opcode, String type) {
		// stackmap frames may refer to 'NEW' instruction offsets when dealing with uninitialized
		// values. because we're causing the location to change, we need to keep track of this and
		// later rewrite any affected frames

		if (opcode == Opcodes.NEW) {
			// label the original location of the new instruction
			Label original = new Label();
			mv.visitLabel(original);

			// inject line-level instrumentation
			instrumentLine();

			// label the replacement location of the new instruction
			Label replacement = new Label();
			mv.visitLabel(replacement);

			// continue with the new op
			super.visitTypeInsn(opcode, type);

			// record the replacement
			newReplacementLocations.add(new NewLocation(original, replacement));
		} else {
			instrumentLine();
			super.visitTypeInsn(opcode, type);
		}
	}

	@Override public void visitVarInsn(int opcode, int var) {
		instrumentLine();
		super.visitVarInsn(opcode, var);

		// for constructors, a RET call is a form of branching, so this sort of complexity before
		// a super constructor call means we can't easily instrument
		if (isConstructor && !hasEntered && opcode == Opcodes.RET) {
			canInstrument = false;
			if (Log.TRACE) Log.trace("method instrumentation", String.format("ret encountered in constructor %s.%s:%s prior to object initialization; unable to instrument", ci.getName(), inspection.getName(), desc));
		}
	}

	@Override public void visitIincInsn(int var, int increment) {
		instrumentLine();
		super.visitIincInsn(var, increment);
	}

	@Override protected void onMethodExit(int opcode) {
		super.onMethodExit(opcode);

		// if we're exiting via a throw, our try/catch will handle it
		if (hasEntered && opcode != Opcodes.ATHROW) {
			instrumentExit(false);
		}
	}

	@Override public void visitMaxs(int maxStack, int maxLocals) {
		// visitMaxs is called after the code is complete, so we close our top level try/catch block here
		if (hasEntered) {
			closeTryCatchWrap();
		}

		// COMPUTE_MAXS will take care of figuring out max stack/locals
		super.visitMaxs(0, 0);
	}

	@Override public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
		// rewrite uninitialized local and stack entries (ASM represents these by using labels)
		// that have been impacted by line level coverage

		Object[] localReplacement = new Object[nLocal];
		Object[] stackReplacement = new Object[nStack];

		for (int i = 0; i < nLocal; ++i) {
			localReplacement[i] = local[i];

			if (local[i] instanceof Label) {
				int offset = ((Label)local[i]).getOffset();

				for (NewLocation nl : newReplacementLocations) {
					if (nl.original.getOffset() == offset)
						localReplacement[i] = nl.replacement;
				}
			}
		}

		for (int i = 0; i < nStack; ++i) {
			stackReplacement[i] = stack[i];

			if (stack[i] instanceof Label) {
				int offset = ((Label)stack[i]).getOffset();

				for (NewLocation nl : newReplacementLocations) {
					if (nl.original.getOffset() == offset)
						stackReplacement[i] = nl.replacement;
				}
			}
		}

		super.visitFrame(type, nLocal, localReplacement, nStack, stackReplacement);
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


	private boolean catchingExceptions = false;

	/** instrumentation to observe exceptions bubbling out of the method */
	private void openTryCatchWrap() {
		// insert start label for the try block
		mv.visitLabel(methodBegin);
		catchingExceptions = true;
	}

	/** instrumentation to observe exceptions bubbling out of the method */
	private void closeTryCatchWrap() {
		if (!catchingExceptions) return;

		// wire up our try/catch around the whole method, so we can observe exception bubbling
		mv.visitTryCatchBlock(methodBegin, methodEnd, methodEnd, null);
		mv.visitLabel(methodEnd);

		// when catching an exception, we need a full frame for the handler
		buildCatchFrame();

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
	private void instrumentExit(boolean inCatchBlock) {
		handler.instrumentExit(mv, methodId, inspection, inCatchBlock);
		if (trackingLines) handler.instrumentLineCoverage(mv, methodId, inspection, lineMapVar);
	}

	/** instrumentation to track line-level execution */
	private void instrumentLine() {
		if (canInstrument && trackingLines && isPendingLineTrace) {
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