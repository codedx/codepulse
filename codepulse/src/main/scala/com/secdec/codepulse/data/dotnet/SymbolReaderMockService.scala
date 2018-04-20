/*
 * Code Pulse: A real-time code coverage testing tool. For more information
 * see http://code-pulse.com
 *
 * Copyright (C) 2017 Applied Visions - http://securedecisions.avi.com
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

package com.secdec.codepulse.data.dotnet

import java.lang.reflect.Modifier

import com.secdec.codepulse.data.{ MethodSignature, MethodTypeParam }

class SymbolReaderMockService extends DotNetBuilder {
	override def Methods: List[(MethodSignature, Int)] = {
		(MethodSignature("ReferenceType1 Namespace1.Namespace1a.Class1.Method1(Int)",
			"Namespace1.Namespace1a.Class1",
			"Class1.cs",
			Modifier.PUBLIC,
			MethodTypeParam.Primitive("Int") :: Nil,
			MethodTypeParam.ReferenceType("ReferenceType1")), 17) ::
		(MethodSignature("ReferenceType1 Namespace1.Namespace1a.Class1.Method2",
			"Namespace1.Namespace1a.Class1",
			"Class1.cs",
			Modifier.PRIVATE,
			Nil,
			MethodTypeParam.ReferenceType("ReferenceType1")), 173) ::
		(MethodSignature("ReferenceType2 Namespace2.Namespace2a.Namespace2b.Class2.Method1",
			"Namespace2.Namespace2a.Namespace2b.Class2",
			"Class2.cs",
			Modifier.PUBLIC & Modifier.SYNCHRONIZED,
			Nil,
			MethodTypeParam.ReferenceType("ReferenceType2")), 187) ::
		(MethodSignature("ReferenceType3 Namespace3.Class3.Method1",
			"Namespace3.Class3",
			"Class3.cs",
			Modifier.ABSTRACT,
			Nil,
			MethodTypeParam.ReferenceType("ReferenceType3")), 374) ::
		Nil
	}
}
