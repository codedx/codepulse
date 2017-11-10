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

package com.secdec.bytefrog.agent.bytefrog.test.cases;

/**
 * A slightly more involved bytefrog test that contains a constructor call with
 * a several layers of super constructors. Due to how constructors must be
 * handled, the super constructor calls will not appear to be from the child
 * constructors.
 *
 * See 'Inaccurate call graph for constructor super calls' at
 * https://trello.com/c/qy9QuivT for more explanation
 *
 * @author RobertF
 */
public class MultipleSuperConstructorTest
{
	public static void main(String[] arguments)
	{
		new ChildClass();
	}

	static class SuperClass1
	{
		public SuperClass1()
		{
		}
	}

	static class SuperClass2 extends SuperClass1
	{
		public SuperClass2()
		{
		}
	}

	static class SuperClass3 extends SuperClass2
	{
		public SuperClass3()
		{
		}
	}

	static class ChildClass extends SuperClass3
	{
		public ChildClass()
		{
		}
	}
}
