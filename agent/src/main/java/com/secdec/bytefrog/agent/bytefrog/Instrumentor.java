/*
 * bytefrog: a tracing framework for the JVM. For more information
 * see http://code-pulse.com/bytefrog
 *
 * Copyright (C) 2014 Applied Visions - http://securedecisions.avi.com
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

package com.secdec.bytefrog.agent.bytefrog;

import java.io.InputStream;
import java.io.IOException;

import com.secdec.bytefrog.asm.ClassReader;
import com.secdec.bytefrog.asm.ClassWriter;

/**
 * Main entry point for instrumenting classes via bytefrog
 *
 * @author RobertF
 */
public class Instrumentor
{
	private Instrumentor()
	{
	}

	private static byte[] instrument(String className, ClassReader cr)
	{
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		TraceClassAdapter adapter = new TraceClassAdapter(cw, className);

		cr.accept(adapter, ClassReader.EXPAND_FRAMES);

		return cw.toByteArray();
	}

	/**
	 * Instruments the class contained in the given buffer.
	 *
	 * @param buffer the buffer of bytes representing the class to instrument
	 * @param name the name of the class being instrumented
	 * @return the instrumented version of the class
	 */
	public static byte[] instrument(String name, byte[] buffer)
	{
		return instrument(name, new ClassReader(buffer));
	}

	/**
	 * Instruments the class contained in the given InputStream.
	 *
	 * @param is the InputStream containing the class to instrument
	 * @param name the name of the class being instrumented
	 * @return the instrumented version of the class
	 */
	public static byte[] instrument(String name, InputStream is) throws IOException
	{
		return instrument(name, new ClassReader(is));
	}

	/**
	 * Instruments the class named.
	 *
	 * @param name the binary name of the class to instrument
	 * @return the instrumented version of the class
	 */
	public static byte[] instrument(String name) throws IOException
	{
		return instrument(name, new ClassReader(name));
	}
}
