package com.codedx.bytefrog.instrumentation.id;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Assigns numeric IDs to classes, storing their information for later retrieval.
  *
  * @author robertf
  */
public class ClassIdentifier {
	private final AtomicInteger nextId = new AtomicInteger();
	private final ConcurrentHashMap<Integer, ClassInformation> map = new ConcurrentHashMap<>();

	public int record(String className, String sourceFile) {
		int id = nextId.getAndIncrement();
		map.put(id, new ClassInformation(className, sourceFile));
		return id;
	}

	public ClassInformation get(int id) {
		return map.get(id);
	}

	/** Stores information about a class. */
	public static class ClassInformation {
		private final String name;
		private final String sourceFile;

		public ClassInformation(String name, String sourceFile) {
			this.name = name;
			this.sourceFile = sourceFile;
		}

		/** Gets the name of the class.
		  * @returns the name of the class
		  */
		public String getName() {
			return name;
		}

		/** Gets the source filename for the class.
		  * @returns the source filename for the class, or null if unknown
		  */
		public String getSourceFile() {
			return sourceFile != null ? sourceFile : "";
		}
	}
}