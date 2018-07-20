package com.secdec.codepulse.data.bytecode.test

import com.secdec.codepulse.data.bytecode.CodePath
import com.secdec.codepulse.data.{MethodSignature, MethodTypeParam}
import org.scalatest.{FunSpec, Matchers}

class CodeTreeSuite extends FunSpec with Matchers {
  describe("Method with generic return value") {
    it("should include generic return value") {
      assert(CodePath.methodSignatureToString(MethodSignature("Method",
        "AClass",
        None,
        1,
        List(),
        MethodTypeParam.ReferenceType("System.Collections.Generic.List<String>"))) == "public List<String> Method()")
    }
  }

  describe("Method with generic and inner generic return value") {
    it("should include both generics in return value") {
      assert(CodePath.methodSignatureToString(MethodSignature("Method",
        "AClass",
        None,
        1,
        List(),
        MethodTypeParam.ReferenceType("System.Collections.Generic.List<System.Collections.Generic.Dictionary<System.String,System.String>>"))) == "public List<Dictionary<String,String>> Method()")
    }
  }

  describe("Method with generic and inner generic return value and parameter count") {
    it("should include both generics in return value") {
      assert(CodePath.methodSignatureToString(MethodSignature("Method",
        "AClass",
        None,
        1,
        List(),
        MethodTypeParam.ReferenceType("List`1<System.Collections.Generic.Dictionary`2<System.String,System.String>>"))) == "public List<Dictionary<String,String>> Method()")
    }
  }

  describe("Method with generic parameter") {
    it("should include generic parameter") {
      assert(CodePath.methodSignatureToString(MethodSignature("Method",
        "AClass",
        None,
        1,
        List(MethodTypeParam.ReferenceType("System.Collections.Generic.List<System.String>")),
        MethodTypeParam.ReferenceType("void"))) == "public void Method(List<String>)")
    }
  }

  describe("Method with generic parameters") {
    it("should include generic parameters") {
      assert(CodePath.methodSignatureToString(MethodSignature("Method",
        "AClass",
        None,
        1,
        List(MethodTypeParam.ReferenceType("System.Collections.Generic.List<System.String>"), MethodTypeParam.ReferenceType("System.Collections.Generic.Dictionary<System.String,int>")),
        MethodTypeParam.ReferenceType("void"))) == "public void Method(List<String>, Dictionary<String,int>)")
    }
  }

  describe("Method with generic parameters and parameter count") {
    it("should include generic parameters") {
      assert(CodePath.methodSignatureToString(MethodSignature("Method",
        "AClass",
        None,
        1,
        List(MethodTypeParam.ReferenceType("System.Collections.Generic.List`1<System.String>"), MethodTypeParam.ReferenceType("System.Collections.Generic.Dictionary`2<System.String,int>")),
        MethodTypeParam.ReferenceType("void"))) == "public void Method(List<String>, Dictionary<String,int>)")
    }
  }

  describe("Method with reference and primitive parameters") {
    it("should include reference and primitive parameters") {
      assert(CodePath.methodSignatureToString(MethodSignature("Method",
        "AClass",
        None,
        1,
        List(MethodTypeParam.ReferenceType("System.String"), MethodTypeParam.Primitive("int")),
        MethodTypeParam.ReferenceType("void"))) == "public void Method(String, int)")
    }
  }

  describe("Method returning reference type") {
    it("should include reference type return") {
      assert(CodePath.methodSignatureToString(MethodSignature("Method",
        "AClass",
        None,
        1,
        List(),
        MethodTypeParam.ReferenceType("System.String"))) == "public String Method()")
    }
  }

  describe("Method returning primitive type") {
    it("should include primitive type return") {
      assert(CodePath.methodSignatureToString(MethodSignature("Method",
        "AClass",
        None,
        1,
        List(),
        MethodTypeParam.ReferenceType("int"))) == "public int Method()")
    }
  }

  describe("Method returning primitive type with reference type parameters") {
    it("should include primitive type return with reference type parameters") {
      assert(CodePath.methodSignatureToString(MethodSignature("Method",
        "AClass",
        None,
        1,
        List(MethodTypeParam.Primitive("int"), MethodTypeParam.ReferenceType("System.String")),
        MethodTypeParam.Primitive("bool"))) == "public bool Method(int, String)")
    }
  }
}
