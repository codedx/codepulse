/*
 * Code Pulse: A real-time code coverage testing tool. For more information
 * see http://code-pulse.com
 *
 * Copyright (C) 2014 Applied Visions - http://securedecisions.avi.com
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

package com.secdec.codepulse.data.model.bytecode

import org.objectweb.asm._
import com.secdec.codepulse.data.MethodSignatureParser
import java.io.InputStream

object AsmVisitors {
	// counterCallback(instructionCount)
	type CounterCallback = Int => Unit

	// methodCallback(methodSignature, instructionCount)
	type MethodCallback = (String, Int) => Unit

	def parseMethodsFromClass(classBytes: InputStream) = {
		val reader = new ClassReader(classBytes)
		val builder = List.newBuilder[(String, Int)]
		val visitor = new ClassStructureVisitor2({ (sig, size) => builder += (sig -> size) })
		reader.accept(visitor, ClassReader.SKIP_FRAMES)
		builder.result()
	}
}

class MethodContentVisitor(counterCallback: AsmVisitors.CounterCallback) extends MethodVisitor(Opcodes.ASM4) {
	private var instructionCounter = 0

	def inc() = instructionCounter += 1
	override def visitCode = { instructionCounter = 0 }
	override def visitEnd = { counterCallback(instructionCounter) }

	override def visitFieldInsn(opcode: Int, owner: String, name: String, desc: String): Unit = inc()
	override def visitIincInsn(v: Int, amt: Int): Unit = inc()
	override def visitInsn(opcode: Int): Unit = inc()
	override def visitIntInsn(opcode: Int, op: Int): Unit = inc()
	override def visitInvokeDynamicInsn(name: String, desc: String, bsm: Handle, bsmArgs: Object*): Unit = inc()
	override def visitJumpInsn(opcode: Int, label: Label): Unit = inc()
	override def visitLdcInsn(cst: Object): Unit = inc()
	override def visitLookupSwitchInsn(dflt: Label, keys: Array[Int], labels: Array[Label]): Unit = inc()
	override def visitMethodInsn(opcode: Int, owner: String, name: String, desc: String): Unit = inc()
	override def visitMultiANewArrayInsn(desc: String, dims: Int): Unit = inc()
	override def visitTableSwitchInsn(min: Int, max: Int, dflt: Label, labels: Label*): Unit = inc()
	override def visitTypeInsn(opcode: Int, tp: String): Unit = inc()
	override def visitVarInsn(opcode: Int, v: Int): Unit = inc()
}

class ClassStructureVisitor2(methodCallback: AsmVisitors.MethodCallback) extends ClassVisitor(Opcodes.ASM4) {
	private var classSignature = ""

	override def visit(version: Int, access: Int, name: String, signature: String, superName: String, interfaces: Array[String]) = {
		classSignature = name
	}

	override def visitMethod(access: Int, name: String, desc: String, sig: String, exceptions: Array[String]): MethodVisitor = {
		val methodSignature = classSignature + "." + name + ";" + access + ";" + desc
		val counterCallback: AsmVisitors.CounterCallback = (insnCount) => {
			methodCallback(methodSignature, insnCount)
		}
		new MethodContentVisitor(counterCallback)
	}
}