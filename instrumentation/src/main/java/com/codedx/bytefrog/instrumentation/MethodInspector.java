package com.codedx.bytefrog.instrumentation;

import com.codedx.bytefrog.thirdparty.asm.Label;
import com.codedx.bytefrog.thirdparty.asm.MethodVisitor;
import com.codedx.bytefrog.thirdparty.asm.Opcodes;

/** Simple method visitor that collects source line information for a method (if available).
  *
  * @author robertf
  */
public class MethodInspector extends MethodVisitor {
	private final String methodName, methodDesc;
	private int startLine = 0, endLine = 0;

	public MethodInspector(String methodName, String methodDesc) {
		super(Opcodes.ASM5);
		this.methodName = methodName;
		this.methodDesc = methodDesc;
	}

	@Override public void visitLineNumber(int line, Label label) {
		super.visitLineNumber(line, label);
		if (line < startLine || startLine == 0) startLine = line;
		if (line > endLine) endLine = line;
	}

	/** Describes the results from pre-instrumentation inspection of a method. */
	public static class Result {
		private final ClassInspector.Result clazz;
		private final String methodName, methodDesc;
		private final int startLine, endLine;

		public ClassInspector.Result getClassInspection() { return clazz; }

		public String getName() { return methodName; }
		public String getDesc() { return methodDesc; }

		public int getStartLine() { return startLine; }
		public int getEndLine() { return endLine; }

		public boolean hasLineInformation() { return startLine > 0; }

		public Result(ClassInspector.Result clazz, String methodName, String methodDesc, int startLine, int endLine) {
			this.clazz = clazz;
			this.methodName = methodName;
			this.methodDesc = methodDesc;
			this.startLine = startLine;
			this.endLine = endLine;
		}
	}

	public Result getResult(ClassInspector.Result clazz) {
		return new Result(clazz, methodName, methodDesc, startLine, endLine);
	}
}