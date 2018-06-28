package com.codedx.codepulse.agent.trace;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.BitSet;

public class InstrumentationMethodVisitor extends MethodVisitor {

    private BitSet lineNumbers = new BitSet();

    public InstrumentationMethodVisitor()
    {
        super(Opcodes.ASM5);
    }

    public BitSet getLineNumbers() { return lineNumbers; }

    @Override
    public void visitCode() {
        lineNumbers.clear();
    }

    @Override
    public void visitLineNumber(int line, Label start)
    {
        int lineNumberVisited = line; // line numbers are 1-based
        lineNumbers.set(lineNumberVisited);
    }
}
