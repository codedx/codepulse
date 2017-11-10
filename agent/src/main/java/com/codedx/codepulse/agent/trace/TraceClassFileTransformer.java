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

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.codedx.bytefrog.thirdparty.minlog.Log;

/**
 * Transformer for instrumenting class files with trace calls.
 * @author RobertF
 */
public class TraceClassFileTransformer implements ClassFileTransformer
{
	private final Pattern selfExclusion;
	private final List<Pattern> exclusions;
	private final List<Pattern> inclusions;

	private final Set<ClassLoader> knownClassLoaders = new HashSet<ClassLoader>();
	private final Set<ClassLoader> failedClassLoaders = new HashSet<ClassLoader>();

	private final Instrumentor instrumentor;
	private final ClassTransformationListener classTransformationListener;

	/**
	 * Constructor
	 * @param exclusions type exclusion regexes
	 */
	public TraceClassFileTransformer(Iterable<String> exclusions, Iterable<String> inclusions,
			Instrumentor instrumentor,
			ClassTransformationListener transListener)
	{
		this.exclusions = new LinkedList<Pattern>();
		this.inclusions = new LinkedList<Pattern>();

		this.instrumentor = instrumentor;

		if (transListener == null)
		{
			this.classTransformationListener = new ClassTransformationListener()
			{
			};
		}
		else
		{
			this.classTransformationListener = transListener;
		}

		this.selfExclusion = Pattern.compile("^(?:com/codedx/codepulse/agent|com/codedx/bytefrog)");

		for (String exclusion : exclusions)
		{
			this.exclusions.add(Pattern.compile(exclusion));
		}

		for (String inclusion : inclusions)
		{
			this.inclusions.add(Pattern.compile(inclusion));
		}
	}

	private boolean shouldExclude(String className)
	{
		if (selfExclusion.matcher(className).lookingAt())
			return true;

		for (Pattern inclusion : inclusions)
		{
			if (inclusion.matcher(className).lookingAt())
				return false;
		}

		for (Pattern exclusion : exclusions)
		{
			if (exclusion.matcher(className).lookingAt())
				return true;
		}

		return false;
	}

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] classfileBuffer)
			throws IllegalClassFormatException
	{
		boolean enableTracing = true;

		// Any excluded classes should not be transformed.
		if (shouldExclude(className))
		{
			classTransformationListener.classIgnored(className, loader);
			enableTracing = false; // no transformation
		}

		// If loader is null, the class was loaded by the bootstrap class
		// loader, which means it probably isn't a class we'll want to rewrite
		// (java/javax/etc)
		else if (loader == null)
		{
			classTransformationListener.classIgnored(className, loader);
			enableTracing = false;
		}

		// Keep track of all known ClassLoaders.
		// Since we are adding calls to Trace's methods, we need to ensure
		// that each ClassLoader knows how to access Trace. If a Class's loader
		// cannot find Trace, then that Class can't be instrumented.

		// If this is the first time we've seen this loader, do checks...
		if (enableTracing && !knownClassLoaders.contains(loader))
		{
			knownClassLoaders.add(loader);

			if (!instrumentor.isTracingAvailable(loader))
				failedClassLoaders.add(loader);
		}

		// If the current class belongs to a "failed" loader, we can't
		// instrument it.
		if (enableTracing && failedClassLoaders.contains(loader))
		{
			classTransformationListener.classTransformFailed(className, loader, null,
					"Cannot instrument class. Cannot access Trace class.");
			enableTracing = false;
		}

		try
		{
			byte[] bytes = instrumentor.instrument(loader, className, classfileBuffer, enableTracing);

			if (enableTracing) classTransformationListener.classTransformed(className, loader);

			return bytes;
		}
		catch (Throwable t)
		{
			Log.error("trace class transformer", "error instrumenting class", t);
			t.printStackTrace();
			if (enableTracing) classTransformationListener.classTransformFailed(className, loader, t,
					"Error during bytecode instrumentation");
			return null;
		}
	}
}
