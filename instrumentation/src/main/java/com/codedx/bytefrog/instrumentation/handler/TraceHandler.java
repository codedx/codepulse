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

package com.codedx.bytefrog.instrumentation.handler;

import com.codedx.bytefrog.instrumentation.MethodInspector;
import org.objectweb.asm.MethodVisitor;

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
	  * @param inCatchBlock whether or not the method exit is in our injected try/catch block
	  */
	void instrumentExit(final MethodVisitor mv, final int methodId, final MethodInspector.Result method, final boolean inCatchBlock);

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