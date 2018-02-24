/* Code Pulse: a real-time code coverage tool, for more information, see <http://code-pulse.com/>
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

package com.secdec.bytefrog.agent.bytefrog.test.util

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URI
import java.net.URLClassLoader
import org.objectweb.asm.ClassReader

import com.codedx.codepulse.agent.trace.Instrumentor
import com.codedx.bytefrog.instrumentation.id._

/** A helper class that can find, instrument, and load classes. Any class loaded will be instrumented.
  *
  * @author robertf
  */
class TestInstrumentor(classIdentifier: ClassIdentifier, methodIdentifier: MethodIdentifier) {

	/** An internal class loader that will prefer to load its own instrumented versions */
	private object instrumentingLoader extends ClassLoader(getClass.getClassLoader) {
		private val system = ClassLoader.getSystemClassLoader

		override protected def loadClass(name: String, resolve: Boolean): Class[_ <: Any] = synchronized {
			val instrument = name startsWith "com.secdec.bytefrog.agent.bytefrog.test.cases"
			var c = findLoadedClass(name)

			// check the system class loader
			if (c == null && system != null) try {
				c = system.loadClass(name)
			} catch {
				case e: ClassNotFoundException => // ignore
			}

			// check ourselves
			if (c == null && instrument) {
				try {
					c = findClass(name)
				} catch {
					case e: ClassNotFoundException => e.printStackTrace // ignore
					case e: IOException => e.printStackTrace // ignore
				}
			}

			// check our parent
			if (c == null) {
				val parent = getParent
				c = parent.loadClass(name)
			}

			if (c == null)
				throw new ClassNotFoundException

			if (resolve)
				resolveClass(c)

			c
		}

		private def findClassFile(name: String) = new File(getClass.getResource(s"/${name.replace('.', '/')}.class").toURI)

		override def findClass(name: String): Class[_ <: Any] = {
			val className = name
			val classFile = findClassFile(className)
			val classLoader = new URLClassLoader(Array(classFile.toURL))
			val classReader = new ClassReader(new FileInputStream(classFile))
			val enableTrace = true
			val bytes = new Instrumentor(classIdentifier, methodIdentifier, null).instrument(classLoader, className, classReader, enableTrace)
			if (bytes != null)
				defineClass(name, bytes, 0, bytes.length)
			else
				null
		}
	}

	def getInstrumentedClass[T](implicit m: Manifest[T]): Class[_ <: Any] = {
		val name = m.runtimeClass.getName
		instrumentingLoader loadClass name
	}
}