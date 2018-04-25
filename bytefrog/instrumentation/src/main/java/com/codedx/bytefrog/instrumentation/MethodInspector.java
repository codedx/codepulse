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

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.BitSet;

/** Simple method visitor that collects source line information for a method (if available).
  *
  * @author robertf
  */
public class MethodInspector extends MethodVisitor {
	private final String methodName, methodDesc;
	private int startLine = 0, endLine = 0;

	private BitSet lines = new BitSet();

	public MethodInspector(String methodName, String methodDesc) {
		super(Opcodes.ASM5);
		this.methodName = methodName;
		this.methodDesc = methodDesc;
	}

	@Override public void visitLineNumber(int line, Label label) {
		super.visitLineNumber(line, label);
		lines.set(line);
		if (line < startLine || startLine == 0) startLine = line;
		if (line > endLine) endLine = line;
	}

	/** Describes the results from pre-instrumentation inspection of a method. */
	public static class Result {
		private final ClassInspector.Result clazz;
		private final String methodName, methodDesc;
		private final int startLine, endLine;
		private final BitSet lines;

		public ClassInspector.Result getClassInspection() { return clazz; }

		public String getName() { return methodName; }
		public String getDesc() { return methodDesc; }

		public int getStartLine() { return startLine; }
		public int getEndLine() { return endLine; }

		public BitSet getLines() { return lines; }

		public boolean hasLineInformation() { return startLine > 0; }

		public Result(ClassInspector.Result clazz, String methodName, String methodDesc, int startLine, int endLine, BitSet lines) {
			this.clazz = clazz;
			this.methodName = methodName;
			this.methodDesc = methodDesc;
			this.startLine = startLine;
			this.endLine = endLine;
			this.lines = lines;
		}
	}

	public Result getResult(ClassInspector.Result clazz) {
		return new Result(clazz, methodName, methodDesc, startLine, endLine, lines);
	}
}