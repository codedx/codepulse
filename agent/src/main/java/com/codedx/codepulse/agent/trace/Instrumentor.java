/* Code Pulse: a real-time code coverage tool, for more information, see <http://code-pulse.com/>
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

package com.codedx.codepulse.agent.trace;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.codedx.bytefrog.filterinjector.FilterInjector;
import com.codedx.bytefrog.filterinjector.adapters.Adapter;
import com.codedx.bytefrog.filterinjector.filter.ParameterlessFilter;

import com.codedx.bytefrog.instrumentation.*;
import com.codedx.bytefrog.instrumentation.id.*;
import com.codedx.bytefrog.instrumentation.handler.*;

import com.codedx.bytefrog.util.ClassLoaderUtil;

import com.codedx.bytefrog.thirdparty.asm.ClassReader;
import com.codedx.bytefrog.thirdparty.asm.ClassVisitor;
import com.codedx.bytefrog.thirdparty.asm.ClassWriter;
import com.codedx.bytefrog.thirdparty.asm.Type;

import com.codedx.bytefrog.thirdparty.minlog.Log;

/** The main glue for instrumentation (including tracing and servlet container filter injection).
  *
  * @author robertf
  */
public class Instrumentor {
	private static Type TRACE_CLASS = Type.getType(com.codedx.codepulse.agent.trace.Trace.class);

	private final ClassIdentifier classIdentifier;
	private final MethodIdentifier methodIdentifier;
	private final File instrumentedDumpTarget;

	private final FilterInjector filterInjector = new FilterInjector(
		new ParameterlessFilter(
			Type.getObjectType("com/codedx/codepulse/agent/trace/TraceFilter"),
			"com.codedx.codepulse-TraceFilter",
			"Code Pulse Trace Filter"
		)
	);

	private final TraceHandler handler = new StandardTraceHandler(
		TRACE_CLASS
	);

	/** Creates a new instance of the instrumentor.
	  * @param classIdentifier a `ClassIdentifier` instance for assigning class IDs
	  * @param methodIdentifier a `MethodIdentifier` instance for assigning method IDs
	  * @param instrumentedDumpTarget a `java.io.File` of a folder to dump instrumented class files in, or null to disable dumping
	  */
	public Instrumentor(ClassIdentifier classIdentifier, MethodIdentifier methodIdentifier, File instrumentedDumpTarget) {
		this.classIdentifier = classIdentifier;
		this.methodIdentifier = methodIdentifier;
		this.instrumentedDumpTarget = instrumentedDumpTarget;
	}

	/** Checks whether or not trace data can be collected within a given classloader (i.e., if the
	  * trace collector class exists in the class loader).
	  */
	public boolean isTracingAvailable(ClassLoader loader) {
		return ClassLoaderUtil.isAvailable(loader, TRACE_CLASS.getClassName());
	}

	/** Instrument a class.
	  * @param loader the ClassLoader being used to load the class
	  * @param className the name of the class being instrumented
	  * @param cr the ClassReader to read the class to be instrumented
	  * @param enableTracing if true, the class will be instrumented for tracing, otherwise, only
	  * 	filter injection will be applied
	  * @returns a byte array containing the instrumented version of the class
	  */
	public byte[] instrument(final ClassLoader classLoader, final String className, final ClassReader cr, boolean enableTracing) {
		final Adapter filterInjectorAdapter = filterInjector.getAdapter(classLoader, cr);

		final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		final ClassVisitor filterInjectorVisitor = filterInjectorAdapter != null ? filterInjectorAdapter.getClassVisitor(classLoader, cw) : null;

		if (enableTracing)
		{
			final ClassInspector inspector = new ClassInspector();
			cr.accept(inspector, 0);

			final ClassInspector.Result inspection = inspector.getResult();
			final int classId = classIdentifier.record(className, inspection.getFileName());

			final ClassInstrumentor ci = new ClassInstrumentor(filterInjectorVisitor != null ? filterInjectorVisitor : cw, methodIdentifier, classId, inspection, handler);
			cr.accept(ci, ClassReader.EXPAND_FRAMES);
		}
		else
		{
			if (filterInjectorVisitor != null)
				cr.accept(filterInjectorVisitor, 0);
			else
				return null;
		}

		if (instrumentedDumpTarget != null) {
			Log.trace("instrumentor", String.format("dumping class %s to %s", className, instrumentedDumpTarget.getPath()));

			final File dump = new File(instrumentedDumpTarget, className + ".instrumented.class");
			dump.getParentFile().mkdirs();

			try (FileOutputStream fw = new FileOutputStream(dump)) {
				fw.write(cw.toByteArray());
			} catch (IOException e) {
				Log.warn("instrumentor", "error dumping instrumented class", e);
			}
		}

		return cw.toByteArray();
	}

	/** Instrument a class.
	  * @param className the name of the class being instrumented
	  * @param buffer the byte array containing the class to be instrumented
	  * @param enableTracing if true, the class will be instrumented for tracing, otherwise, only
	  * 	filter injection will be applied
	  * @returns a byte array containing the instrumented version of the class
	  */
	public byte[] instrument(final ClassLoader classLoader, final String className, final byte[] buffer, boolean enableTracing) {
		return instrument(classLoader, className, new ClassReader(buffer), enableTracing);
	}
}