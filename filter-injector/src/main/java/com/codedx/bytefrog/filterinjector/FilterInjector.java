package com.codedx.bytefrog.filterinjector;

import com.codedx.bytefrog.filterinjector.adapters.*;
import com.codedx.bytefrog.filterinjector.filter.InjectableFilter;

import com.codedx.bytefrog.thirdparty.asm.ClassReader;
import com.codedx.bytefrog.thirdparty.asm.ClassVisitor;
import com.codedx.bytefrog.thirdparty.asm.Opcodes;

import com.codedx.bytefrog.thirdparty.minlog.Log;

/** A helper utility that, via instrumentation, can inject a filter into one of several known
  * servlet containers.
  *
  * @author robertf
  */
public class FilterInjector {
	private final InjectableFilter filter;

	public FilterInjector(InjectableFilter filter) {
		this.filter = filter;
	}

	public Adapter getAdapter(final ClassLoader classLoader, final byte[] buffer) {
		return getAdapter(classLoader, new ClassReader(buffer));
	}

	public Adapter getAdapter(final ClassLoader classLoader, final ClassReader cr) {
		final Visitor visitor = new Visitor();
		cr.accept(visitor, 0);
		return visitor.getAdapter();
	}

	private Adapter checkAdapter(String name, String[] interfaces) {
		for (String iface : interfaces) switch (iface) {
			case "org/apache/catalina/Context":
				Log.debug("filter injector", "encountered org.apache.catalina.Context; checking for tomcat");
				return new TomcatAdapter(filter);
		}

		switch (name) {
			case "org/eclipse/jetty/servlet/ServletHandler":
				Log.debug("filter injector", "encountered org.eclipse.jetty.servlet.ServletHandler; checking for jetty");
				return new JettyAdapter(filter);
		}

		return null;
	}

	private class Visitor extends ClassVisitor {
		private Adapter adapter = null;

		public Visitor() {
			super(Opcodes.ASM5);
		}

		public Adapter getAdapter() {
			return adapter;
		}

		@Override public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			super.visit(version, access, name, signature, superName, interfaces);
			adapter = checkAdapter(name, interfaces);
		}
	}
}