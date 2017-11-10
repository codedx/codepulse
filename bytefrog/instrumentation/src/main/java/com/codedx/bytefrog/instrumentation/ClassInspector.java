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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.codedx.bytefrog.thirdparty.asm.ClassVisitor;
import com.codedx.bytefrog.thirdparty.asm.MethodVisitor;
import com.codedx.bytefrog.thirdparty.asm.Opcodes;

/** Simple class visitor that collects inspection data from MethodInspector.
  *
  * @author robertf
  */
public class ClassInspector extends ClassVisitor {
	private String fileName = null;
	private LinkedList<MethodInspector> inspectors = new LinkedList<>();

	public ClassInspector() {
		super(Opcodes.ASM5);
	}

	@Override public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override public void visitSource(String source, String debug) {
		this.fileName = source;
		super.visitSource(source, debug);
	}

	@Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		final MethodInspector mi = new MethodInspector(name, desc);
		inspectors.push(mi);
		return mi;
	}

	/** Describes the results from pre-instrumentation inspection of a class. */
	public static class Result {
		private final String fileName;

		public String getFileName() { return fileName; }

		public Result(String fileName, LinkedList<MethodInspector> inspectors) {
			this.fileName = fileName;

			for (MethodInspector mi : inspectors) {
				final MethodInspector.Result inspection = mi.getResult(this);

				if (inspection != null)
					methods.put(inspection.getName() + ":" + inspection.getDesc(), inspection);
			}
		}

		private final Map<String, MethodInspector.Result> methods = new HashMap<>();

		/** Lookup the described method's inspection result.
		  * @returns the inspection result, or null if none was found
		  */
		public MethodInspector.Result lookupMethod(String name, String desc) {
			return methods.get(name + ":" + desc);
		}
	}

	public Result getResult() {
		return new Result(fileName, inspectors);
	}
}