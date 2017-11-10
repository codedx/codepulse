package com.codedx.codepulse.agent.trace;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.codedx.bytefrog.instrumentation.*;
import com.codedx.bytefrog.instrumentation.id.*;
import com.codedx.bytefrog.instrumentation.handler.*;

import com.codedx.bytefrog.util.ClassLoaderUtil;

import com.codedx.bytefrog.thirdparty.asm.ClassReader;
import com.codedx.bytefrog.thirdparty.asm.ClassVisitor;
import com.codedx.bytefrog.thirdparty.asm.ClassWriter;
import com.codedx.bytefrog.thirdparty.asm.Type;

import com.codedx.bytefrog.thirdparty.minlog.Log;

/** The main glue for instrumenting a class.
  * @author robertf
  */
public class Instrumentor {
	private static Type TRACE_CLASS = Type.getType(com.codedx.codepulse.agent.trace.Trace.class);

	private final ClassIdentifier classIdentifier;
	private final MethodIdentifier methodIdentifier;
	private final File instrumentedDumpTarget;

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
	public boolean isAvailable(ClassLoader loader) {
		return ClassLoaderUtil.isAvailable(loader, TRACE_CLASS.getClassName());
	}

	/** Instrument a class for tracing.
	  * @param loader the ClassLoader being used to load the class
	  * @param className the name of the class being instrumented
	  * @param cr the ClassReader to read the class to be instrumented
	  * @returns a byte array containing the instrumented version of the class
	  */
	public byte[] instrument(final ClassLoader classLoader, final String className, final ClassReader cr) {
		final ClassInspector inspector = new ClassInspector();
		cr.accept(inspector, 0);

		final ClassInspector.Result inspection = inspector.getResult();
		final int classId = classIdentifier.record(className, inspection.getFileName());

		final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		final ClassVisitor cv = cw;

		final ClassInstrumentor ci = new ClassInstrumentor(cv, methodIdentifier, classId, inspection, handler);
		cr.accept(ci, ClassReader.EXPAND_FRAMES);

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

	/** Instrument a class for tracing.
	  * @param className the name of the class being instrumented
	  * @param buffer the byte array containing the class to be instrumented
	  * @returns a byte array containing the instrumented version of the class
	  */
	public byte[] instrument(final ClassLoader classLoader, final String className, final byte[] buffer) {
		return instrument(classLoader, className, new ClassReader(buffer));
	}
}