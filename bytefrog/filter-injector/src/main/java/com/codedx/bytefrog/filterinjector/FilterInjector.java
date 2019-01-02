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

package com.codedx.bytefrog.filterinjector;

import com.codedx.bytefrog.filterinjector.adapters.*;
import com.codedx.bytefrog.filterinjector.filter.InjectableFilter;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import com.esotericsoftware.minlog.Log;

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
			super(Opcodes.ASM7);
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