// dotnet-symbol-service
//
// Copyright (C) 2017 Applied Visions - http://securedecisions.avi.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

using System;
using System.Collections.Generic;

namespace SymbolService.Model
{
	public class MethodInfo
	{
        public Guid Id { get; }

		public String FullyQualifiedName { get; set; }

		public String ContainingClass { get; set; }

		public String File { get; set; }

		public int AccessModifiers { get; set; }

		public IEnumerable<String> Parameters { get; set; }

		public String ReturnType { get; set; }

		public int Instructions { get; set; }

        public Guid SurrogateFor { get; set; }

		public int SequencePointCount { get; set; }


		public MethodInfo()
	    {
	        Id = Guid.NewGuid();
	    }
    }
}
