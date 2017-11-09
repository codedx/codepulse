package com.codedx.bytefrog.util

import org.scalatest._

import com.codedx.bytefrog.thirdparty.asm.Type;

class ClassLoaderUtilSpec extends fixture.FlatSpec {
	behavior of "ClassLoaderUtil"

	case class FixtureParam(emptyClassLoader: ClassLoader, systemClassLoader: ClassLoader)

	def withFixture(test: OneArgTest) = {
		object emptyClassLoader extends ClassLoader(null)
		object systemClassLoader extends ClassLoader()

		val fixture = FixtureParam(emptyClassLoader, systemClassLoader)

		withFixture(test.toNoArgTest(fixture))
	}

	it should "return false for non-existant classes" in { f =>
		assertResult(false) {
			ClassLoaderUtil.isAvailable(f.emptyClassLoader, "foo")
		}
	}

	it should "return true for existing classes" in { f =>
		assertResult(true) {
			ClassLoaderUtil.isAvailable(f.systemClassLoader, "java.lang.String")
		}
	}

	it should "be able to inject classes" in { f =>
		class dummyType
		val dummyType = Type getType classOf[dummyType]

		assertResult(false) {
			ClassLoaderUtil.isAvailable(f.emptyClassLoader, dummyType.getClassName)
		}

		ClassLoaderUtil.injectClass(f.emptyClassLoader, dummyType)

		assertResult(true) {
			ClassLoaderUtil.isAvailable(f.emptyClassLoader, dummyType.getClassName)
		}
	}
}