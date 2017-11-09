package com.codedx.bytefrog.filterinjector.adapters;

import com.codedx.bytefrog.filterinjector.filter.InjectableFilter;
import com.codedx.bytefrog.util.ClassLoaderUtil;

import com.codedx.bytefrog.thirdparty.asm.*;
import com.codedx.bytefrog.thirdparty.minlog.Log;

/** For Jetty, injects the filter into instances of Jetty's ServletHandler.
  *
  * We instrument the constructor of org.eclipse.jetty.servlet.ServletHandler to inject a call to
  * add the supplied filter.
  *
  * @author robertf
  */
public class JettyAdapter implements Adapter {
	private final InjectableFilter filter;

	public JettyAdapter(InjectableFilter filter) {
		this.filter = filter;
	}

	public ClassVisitor getClassVisitor(final ClassLoader classLoader, final ClassVisitor cv) {
		if (ClassLoaderUtil.isAvailable(classLoader, "org.eclipse.jetty.servlet.FilterHolder") && ClassLoaderUtil.isAvailable(classLoader, "org.eclipse.jetty.servlet.FilterMapping")) {
			// jetty
			Log.debug("jetty adapter", "injecting filter for jetty");
			if (filter.isAvailable(classLoader))
				return new Visitor(cv, filter);
		}

		return null;
	}

	private static class Visitor extends ClassVisitor {
		private final InjectableFilter filter;

		public Visitor(final ClassVisitor cv, final InjectableFilter filter) {
			super(Opcodes.ASM5, cv);
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
				return new ConstructorInstrumentor(mv, className, filter);

			return mv;
		}
	}

	private static class ConstructorInstrumentor extends MethodVisitor {
		private final String className;
		private final InjectableFilter filter;

		public ConstructorInstrumentor(final MethodVisitor mv, final String className, final InjectableFilter filter) {
			super(Opcodes.ASM5, mv);

			this.className = className;
			this.filter = filter;

			if (Log.DEBUG) Log.debug("jetty adapter", String.format("instrumenting %s constructor", className));
		}

		@Override public void visitInsn(int opcode) {
			if (opcode == Opcodes.RETURN) {
				/* Essentially, we're injecting the following code:
					holder = new FilterHolder(filter);
					holder.setName(...)
					this.addFilterWithMapping(holder, "/*", FilterMapping.REQUEST)
				*/

				Type servletFilter = Type.getObjectType("javax/servlet/Filter");
				Type filterHolder = Type.getObjectType("org/eclipse/jetty/servlet/FilterHolder");
				Type filterMapping = Type.getObjectType("org/eclipse/jetty/servlet/FilterMapping");
				Type context = Type.getObjectType("org/eclipse/jetty/servlet/ServletHandler");

				// build the filter
				filter.constructFilter(mv);
				// new FilterHolder(filter)
				mv.visitTypeInsn(Opcodes.NEW, filterHolder.getInternalName());
				mv.visitInsn(Opcodes.DUP_X1);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, filterHolder.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, servletFilter), false);
				// .setName(<filter.getName()>)
				mv.visitInsn(Opcodes.DUP);
				mv.visitLdcInsn(filter.getName());
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, filterHolder.getInternalName(), "setName", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), false);
				// this.addFilterWithMapping(holder, "/*", FilterMapping.REQUEST)
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitLdcInsn("/*");
				mv.visitFieldInsn(Opcodes.GETSTATIC, filterMapping.getInternalName(), "REQUEST", Type.INT_TYPE.getDescriptor());
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, "addFilterWithMapping", Type.getMethodDescriptor(Type.VOID_TYPE, filterHolder, Type.getType(String.class), Type.INT_TYPE), false);
			}

			super.visitInsn(opcode);
		}
	}
}