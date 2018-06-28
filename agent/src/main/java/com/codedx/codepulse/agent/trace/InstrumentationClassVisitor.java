package com.codedx.codepulse.agent.trace;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.BitSet;
import java.util.LinkedList;

public class InstrumentationClassVisitor extends ClassVisitor {

    private LinkedList<InstrumentationMethodVisitor> inspectors = new LinkedList<>();

    public InstrumentationClassVisitor() {
        super(Opcodes.ASM5);
    }

    public BitSet getLineNumbers() {
        BitSet lineNumbers = new BitSet();
        for (InstrumentationMethodVisitor visitor : inspectors) {
            lineNumbers.or(visitor.getLineNumbers());
        }
        return lineNumbers;
    }

    @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        InstrumentationMethodVisitor visitor = new InstrumentationMethodVisitor();
        inspectors.add(visitor);
        return visitor;
    }
}
