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

package com.secdec.bytefrog.agent.trace;

/**
 * An instance of this class is required in order to use the
 * {@link TraceClassFileTransformer}, allowing it to report progress and errors
 * for each class that it encounters.
 * 
 * @author DylanH
 */
public abstract class ClassTransformationListener
{

	/**
	 * Called when a class has successfully been transformed. Default
	 * implementation is a No-Op.
	 * 
	 * @param className The name of the transformed class
	 * @param loader The ClassLoader for the transformed class
	 */
	public void classTransformed(String className, ClassLoader loader)
	{
		// default implementation is a No-Op
	}

	/**
	 * Called when a class has been ignored by the class transformer. Default
	 * implementation is a No-Op.
	 * 
	 * @param className The name of the class
	 * @param loader The ClassLoader for the class
	 */
	public void classIgnored(String className, ClassLoader loader)
	{
		// default implementation is a No-Op
	}

	/**
	 * Called when a class cannot be transformed for some reason. This method is
	 * the transformer's means of error reporting. Default implementation is a
	 * No-Op; it is strongly encouraged to override this method.
	 * 
	 * @param className The name of the class that couldn't be instrumented.
	 *            This field may be left <code>null</code>, in which case the
	 *            error is one concerning the ClassLoader, with no specific
	 *            class as a context.
	 * @param loader The ClassLoader under which the Class is being transformed.
	 *            Many possible errors can arise because of a ClassLoader being
	 *            uncooperative.
	 * @param cause An optional Throwable that may be the root cause of the
	 *            error. May be left <code>null</code>.
	 * @param message A human-readable message that gives a brief description of
	 *            the reason why the class couldn't be transformed.
	 */
	public void classTransformFailed(String className, ClassLoader loader, Throwable cause,
			String message)
	{
		// default implementation is a No-Op
	}
}
