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

package com.codedx.bytefrog.filterinjector.adapters;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.codedx.bytefrog.filterinjector.filter.InjectableFilter;
import com.codedx.bytefrog.util.ClassLoaderUtil;

import org.objectweb.asm.*;
import com.esotericsoftware.minlog.Log;

/** For Tomcat, injects the filter into Tomcat contexts.
  *
  * We look for implementations of org.apache.catalina.Context and inject a call at the end of the
  * constructor to addFilterMapBefore to add our filter to the beginning(-ish) for all chains
  * created for that context.
  *
  * @author robertf
  */
public class TomcatAdapter implements Adapter {
	private final InjectableFilter filter;

	public TomcatAdapter(InjectableFilter filter) {
		this.filter = filter;
	}

	public ClassVisitor getClassVisitor(final ClassLoader classLoader, final ClassVisitor cv) {
		if (isTomcat(classLoader)) {
			if (ClassLoaderUtil.isAvailable(classLoader, "org.apache.tomcat.util.descriptor.web.FilterDef") && ClassLoaderUtil.isAvailable(classLoader, "org.apache.tomcat.util.descriptor.web.FilterMap")) {
				// tomcat 8+
				Log.debug("tomcat adapter", "using org.apache.tomcat.util.descriptor.web def/map");
				if (filter.isAvailable(classLoader))
					return new Visitor(
						cv,
						Type.getObjectType("org/apache/tomcat/util/descriptor/web/FilterDef"),
						Type.getObjectType("org/apache/tomcat/util/descriptor/web/FilterMap"),
						filter
					);
			} else if (ClassLoaderUtil.isAvailable(classLoader, "org.apache.catalina.deploy.FilterDef") && ClassLoaderUtil.isAvailable(classLoader, "org.apache.catalina.deploy.FilterMap")) {
				// tomcat 5-7, maybe earlier?
				Log.debug("tomcat adapter", "using org.apache.catalina.deploy def/map");
				if (filter.isAvailable(classLoader))
					return new Visitor(
						cv,
						Type.getObjectType("org/apache/catalina/deploy/FilterDef"),
						Type.getObjectType("org/apache/catalina/deploy/FilterMap"),
						filter
					);
			}
		}

		return null;
	}

	private boolean isTomcat(ClassLoader classLoader) {
		try {
			Class<?> serverInfo = classLoader.loadClass("org.apache.catalina.util.ServerInfo");
			Method getServerNumber = serverInfo.getMethod("getServerNumber");
			String version = (String)getServerNumber.invoke(null);
			Log.info("tomcat adapter", String.format("detected tomcat version %s", version));
			return true;
		} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			Log.error("tomcat adapter", "error checking for tomcat; skipping", e);
		}

		return false;
	}

	private static class Visitor extends ClassVisitor {
		private final Type filterDef, filterMap;
		private final InjectableFilter filter;

		public Visitor(final ClassVisitor cv, final Type filterDef, final Type filterMap, final InjectableFilter filter) {
			super(Opcodes.ASM7, cv);

			this.filterDef = filterDef;
			this.filterMap = filterMap;
			this.filter = filter;
		}

		private String className = "";
		@Override public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			super.visit(version, access, name, signature, superName, interfaces);
			this.className = name;
		}

		@Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

			if ((access & Opcodes.ACC_PUBLIC) == Opcodes.ACC_PUBLIC && name.equals("<init>"))
				return new ConstructorInstrumentor(mv, className, filterDef, filterMap, filter);

			return mv;
		}
	}

	private static class ConstructorInstrumentor extends MethodVisitor {
		private final String className;
		private final Type filterDef, filterMap;
		private final InjectableFilter filter;

		public ConstructorInstrumentor(final MethodVisitor mv, final String className, final Type filterDef, final Type filterMap, final InjectableFilter filter) {
			super(Opcodes.ASM7, mv);

			this.className = className;
			this.filterDef = filterDef;
			this.filterMap = filterMap;
			this.filter = filter;

			if (Log.DEBUG) Log.debug("tomcat adapter", String.format("instrumenting %s constructor", className));
		}

		@Override public void visitInsn(int opcode) {
			if (opcode == Opcodes.RETURN) {
				/* Essentially, we're injecting the following code:
					def = new org.apache.tomcat.util.descriptor.web.FilterDef();
					def.setFilterName(<filter.getName()>);
					def.setDisplayName(<filter.getDisplayName()>);
					def.setFilter(filter);
					def.setFilterClass(filter.getClass().getName());
					ctx.addFilterDef(def);
					map = new org.apache.tomcat.util.descriptor.web.FilterMap()
					map.setFilterName(def.getFilterName());
					map.addURLPattern("*");
					ctx.addFilterMapBefore(map);
				*/

				Type context = Type.getObjectType("org/apache/catalina/Context");
				Type servletFilter = Type.getObjectType("javax/servlet/Filter");

				// build filter definition:
				// new org.apache.tomcat.util.descriptor.web.FilterDef()
				mv.visitTypeInsn(Opcodes.NEW, filterDef.getInternalName());
				mv.visitInsn(Opcodes.DUP);
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, filterDef.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE), false);
				// .setFilterName(<filter.getName()>)
				mv.visitInsn(Opcodes.DUP);
				mv.visitLdcInsn(filter.getName());
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, filterDef.getInternalName(), "setFilterName", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), false);
				// .setDisplayName(<filter.getDisplayName()>)
				mv.visitInsn(Opcodes.DUP);
				mv.visitLdcInsn(filter.getDisplayName());
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, filterDef.getInternalName(), "setDisplayName", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), false);
				// .setFilter(< new filter >)
				mv.visitInsn(Opcodes.DUP);
				filter.constructFilter(mv);
				mv.visitInsn(Opcodes.DUP2);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, filterDef.getInternalName(), "setFilter", Type.getMethodDescriptor(Type.VOID_TYPE, servletFilter), false);
				// .setFilterClass(filter.getClass().getName())
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Object.class), "getClass", Type.getMethodDescriptor(Type.getType(Class.class)), false);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Class.class), "getName", Type.getMethodDescriptor(Type.getType(String.class)), false);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, filterDef.getInternalName(), "setFilterClass", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), false);
				// this.addFilterDef(def)
				mv.visitInsn(Opcodes.DUP);
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, "addFilterDef", Type.getMethodDescriptor(Type.VOID_TYPE, filterDef), false);

				// build filter map:
				// new org.apache.tomcat.util.descriptor.web.FilterMap()
				mv.visitTypeInsn(Opcodes.NEW, filterMap.getInternalName());
				mv.visitInsn(Opcodes.DUP);
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, filterMap.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE), false);
				// .setFilterName(def.getFilterName())
				mv.visitInsn(Opcodes.SWAP);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, filterDef.getInternalName(), "getFilterName", Type.getMethodDescriptor(Type.getType(String.class)), false);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn(Opcodes.DUP_X1);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, filterMap.getInternalName(), "setFilterName", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), false);
				// .addURLPattern("*")
				mv.visitInsn(Opcodes.DUP);
				mv.visitLdcInsn("*");
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, filterMap.getInternalName(), "addURLPattern", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), false);
				// this.addFilterMapBefore(map)
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, "addFilterMapBefore", Type.getMethodDescriptor(Type.VOID_TYPE, filterMap), false);
			}

			super.visitInsn(opcode);
		}
	}
}