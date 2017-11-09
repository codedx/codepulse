package com.codedx.bytefrog.filterinjector.adapters;

import com.codedx.bytefrog.thirdparty.asm.ClassVisitor;

/** Adapters are used for injecting servlet filters into a container at runtime, in a generic/
  * extensible manner. Adapterss are enumerated in FilterInjector such that an instrumentation
  * process can easily determine an appropriate injection method based on what classes are loaded
  * at runtime.
  *
  * @author robertf
  */
public interface Adapter {
	/** Returns a ClassVisitor to handle instrumentation of the class as necessary.
    * @param classLoader the class loader being used to load the class
	  * @param cv the ClassVisitor to delegate to (e.g., the one that accepts the instrumented class)
	  */
	ClassVisitor getClassVisitor(final ClassLoader classLoader, final ClassVisitor cv);
}