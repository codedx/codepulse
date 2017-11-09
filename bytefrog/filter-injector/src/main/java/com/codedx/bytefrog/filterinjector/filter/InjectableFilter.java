package com.codedx.bytefrog.filterinjector.filter;

import com.codedx.bytefrog.thirdparty.asm.MethodVisitor;

/** InjectableFilter is an adapter for building bytecode to construct a filter for injection.
  *
  * @author robertf
  */
public interface InjectableFilter {
	/** Gets a name for the filter, to be passed to the servlet container as necessary */
	String getName();

	/** Gets a display name for the filter, to be passed to the servlet container as necessary */
	String getDisplayName();

	/** Checks if this filter is usable with the given classloader. Some implementations may try to
	  * inject the filter code into the class loader to make it available.
	  * @param cl the classloader to check
	  */
	boolean isAvailable(final ClassLoader cl);

	/** Constructs an instance of the injectable filter, leaving its reference on the stack.
	  * @param mv the methodVisitor to build the filter in
	  */
	void constructFilter(final MethodVisitor mv);
}