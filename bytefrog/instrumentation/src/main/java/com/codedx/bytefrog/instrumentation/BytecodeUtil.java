package com.codedx.bytefrog.instrumentation;

import com.codedx.bytefrog.thirdparty.asm.MethodVisitor;
import com.codedx.bytefrog.thirdparty.asm.Opcodes;

/** Some basic bytecode helper utilities
  *
  * @author robertf
  */
public class BytecodeUtil {
	// static
	private BytecodeUtil() {}

	/** Push an integer if it's small enough, otherwise, load it as a constant on the stack.
	  * @param mv the method visitor
	  * @param value the integer value
	  */
	public static void pushInt(final MethodVisitor mv, final int value) {
		if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE)
			mv.visitIntInsn(Opcodes.BIPUSH, value);
		else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE)
			mv.visitIntInsn(Opcodes.SIPUSH, value);
		else
			mv.visitLdcInsn(new Integer(value));
	}
}