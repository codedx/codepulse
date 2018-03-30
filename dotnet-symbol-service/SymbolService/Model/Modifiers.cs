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

namespace SymbolService.Model
{
	public enum Modifier
	{
		PUBLIC = 0x00000001,
		PRIVATE = 0x00000002,
		PROTECTED = 0x00000004,
		STATIC = 0x00000008,
		FINAL = 0x00000010,
		SYNCHRONIZED = 0x00000020,
		VOLATILE = 0x00000040,
		TRANSIENT = 0x00000080,
		NATIVE = 0x00000100,
		INTERFACE = 0x00000200,
		ABSTRACT = 0x00000400,
		STRICT = 0x00000800,

		BRIDGE = 0x00000040,
		VARARGS = 0x00000080,
		SYNTHETIC = 0x00001000,
		ANNOTATION = 0x00002000,
		ENUM = 0x00004000,
		MANDATED = 0x00008000
	}
}
