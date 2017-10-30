/*
 * bytefrog: a tracing framework for the JVM. For more information
 * see http://code-pulse.com/bytefrog
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

package com.secdec.bytefrog.agent.bytefrog.test.cases;

/**
 * A slightly more involved bytefrog test that contains a constructor call with
 * a super constructor. Due to how constructors must be handled, the super
 * constructor call will not appear to be from the child constructor.
 * 
 * See 'Inaccurate call graph for constructor super calls' at
 * https://trello.com/c/qy9QuivT for more explanation
 * 
 * @author RobertF
 */
public class SuperConstructorTest
{
	public static void main(String[] arguments)
	{
		new ChildClass();
	}

	static class SuperClass
	{
		public SuperClass()
		{
		}
	}

	static class ChildClass extends SuperClass
	{
		public ChildClass()
		{
		}
	}
}
