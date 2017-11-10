package com.codedx.bytefrog.filterinjector.filter;

import com.codedx.bytefrog.util.ClassLoaderUtil;

import com.codedx.bytefrog.thirdparty.asm.MethodVisitor;
import com.codedx.bytefrog.thirdparty.asm.Opcodes;
import com.codedx.bytefrog.thirdparty.asm.Type;

/** An InjectableFilter adapter for filters with a parameterless constructor.
  *
  * @author robertf
  */
public class ParameterlessFilter implements InjectableFilter {
	private final Type filterType;
	private final String name, displayName;

	public String getName() { return name; }
	public String getDisplayName() { return displayName; }

	public ParameterlessFilter(Type filterType, String name, String displayName) {
		this.filterType = filterType;
		this.name = name;
		this.displayName = displayName;
	}

	public boolean isAvailable(final ClassLoader cl) {
		return ClassLoaderUtil.injectClass(cl, filterType);
	}

	public void constructFilter(final MethodVisitor mv) {
		// new <filterType>()
		mv.visitTypeInsn(Opcodes.NEW, filterType.getInternalName());
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, filterType.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE), false);
	}
}