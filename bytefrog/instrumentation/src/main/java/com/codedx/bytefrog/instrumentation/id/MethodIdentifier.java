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

package com.codedx.bytefrog.instrumentation.id;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Assigns numeric IDs to method signatures, storing their signature for later retrieval.
  *
  * @author robertf
  */
public class MethodIdentifier {
	private final AtomicInteger nextId = new AtomicInteger();
	protected final ConcurrentHashMap<Integer, MethodInformation> map = new ConcurrentHashMap<>();

	public int record(int classId, int access, String methodName, String descriptor, int startLine, int endLine) {
		int id = nextId.getAndIncrement();
		map.put(id, new MethodInformation(classId, access, methodName, descriptor, startLine, endLine));
		return id;
	}

	public MethodInformation get(int id) {
		return map.get(id);
	}

	/** Stores information about a method. */
	public static class MethodInformation {
		private final int classId;
		private final int access;
		private final String name;
		private final String descriptor;
		private final int startLine, endLine;

		public MethodInformation(int classId, int access, String name, String descriptor, int startLine, int endLine) {
			this.classId = classId;
			this.access = access;
			this.name = name;
			this.descriptor = descriptor;
			this.startLine = startLine;
			this.endLine = endLine;
		}

		/** Gets the id of the method's parent class.
		  * @returns the id of the method's parent class
		  */
		public int getClassId() {
			return classId;
		}

		/** Gets the access flags (see ASM's Opcodes class) for the method.
		  * @returns the access flags
		  */
		public int getAccess() {
			return access;
		}

		/** Gets the name of the method.
		  * @returns the name of the method
		  */
		public String getName() {
			return name;
		}

		/** Gets the descriptor of the method.
		  * @returns the descriptor of the method
		  */
		public String getDescriptor() {
			return descriptor;
		}

		/** Gets the start line of the method.
		  * @returns the start line of the method, or -1 if debug information is not available
		  */
		public int getStartLine() {
			return startLine;
		}

		/** Gets the end line of the method.
		  * @returns the end line of the method, or -1 if debug information is not available
		  */
		public int getEndLine() {
			return endLine;
		}
	}
}