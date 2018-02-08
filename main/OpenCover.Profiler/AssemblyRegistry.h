// Copyright 2017 Secure Decisions, a division of Applied Visions, Inc. 
// Permission is hereby granted, free of charge, to any person obtaining a copy of 
// this software and associated documentation files (the "Software"), to deal in the 
// Software without restriction, including without limitation the rights to use, copy, 
// modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, 
// and to permit persons to whom the Software is furnished to do so, subject to the 
// following conditions:
// 
// The above copyright notice and this permission notice shall be included in all copies 
// or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
// INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A 
// PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT 
// HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION 
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE 
// SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
// This material is based on research sponsored by the Department of Homeland
// Security (DHS) Science and Technology Directorate, Cyber Security Division
// (DHS S&T/CSD) via contract number HHSP233201600058C.

#pragma once
#include <map>

namespace Injection
{
	struct AssemblyVersion
	{
		USHORT majorVersion;
		USHORT minorVersion;
		USHORT buildNumber;
		USHORT revisionNumber;

		bool operator==(const AssemblyVersion& other) const
		{
			return majorVersion == other.majorVersion &&
				minorVersion == other.minorVersion &&
				buildNumber == other.buildNumber &&
				revisionNumber == other.revisionNumber;
		}

		bool operator!=(const AssemblyVersion& other) const
		{
			return !(*this == other);
		}

		friend bool operator<=(const AssemblyVersion& v1, const AssemblyVersion& v2)
		{
			return v1 == v2 || !(v1 > v2);
		}

		friend bool operator<(const AssemblyVersion& v1, const AssemblyVersion& v2)
		{
			return v1 != v2 && !(v1 > v2);
		}

		friend bool operator>=(const AssemblyVersion& v1, const AssemblyVersion& v2)
		{
			return v1 == v2 || v1 > v2;
		}

		friend bool operator>(const AssemblyVersion& v1, const AssemblyVersion& v2)
		{
			if (v1.majorVersion > v2.majorVersion) return true;
			if (v1.majorVersion < v2.majorVersion) return false;

			if (v1.minorVersion > v2.minorVersion) return true;
			if (v1.minorVersion < v2.minorVersion) return false;

			if (v1.buildNumber > v2.buildNumber) return true;
			if (v1.buildNumber < v2.buildNumber) return false;

			return (v1.revisionNumber > v2.revisionNumber);
		}

	};

	struct AssemblyReference
	{
		std::wstring name;
		BYTE publicKeyToken[8];
		AssemblyVersion version;

		bool operator==(const AssemblyReference& other) const
		{
			return name == other.name &&
				version == other.version &&
				publicKeyToken[0] == other.publicKeyToken[0] &&
				publicKeyToken[1] == other.publicKeyToken[1] &&
				publicKeyToken[2] == other.publicKeyToken[2] &&
				publicKeyToken[3] == other.publicKeyToken[3] &&
				publicKeyToken[4] == other.publicKeyToken[4] &&
				publicKeyToken[5] == other.publicKeyToken[5] &&
				publicKeyToken[6] == other.publicKeyToken[6] &&
				publicKeyToken[7] == other.publicKeyToken[7];
		}

		bool operator!=(const AssemblyReference& other) const
		{
			return !(*this == other);
		}
	};

	class AssemblyRegistry
	{
	public:
		AssemblyRegistry(const ATL::CComPtr<ICorProfilerInfo>& profilerInfo);

		bool FillAssembliesByName(const std::wstring& name, std::vector<AssemblyReference>& referencedAssemblies) const;

		bool FindMaxAssemblyVersion(const std::wstring& name, AssemblyVersion& assemblyVersion) const;
		bool FindMaxAssemblyVersion(const std::wstring& name, AssemblyReference& assemblyReference) const;

		HRESULT RecordAssemblyMetadataForModule(const ModuleID moduleId);

	private:

		bool StoreAssemblyDetails(unsigned char* pbPublicKeyOrToken, const ULONG cbPublicKeyOrToken, const ALG_ID hashAlgId, wchar_t* pzName, const ULONG chNameOut, const ASSEMBLYMETADATA& metaData, const DWORD flags);

		ATL::CComPtr<ICorProfilerInfo> m_profilerInfo;

		std::map<std::wstring, std::vector<AssemblyReference>> m_assemblyVersionRegistry;
	};
}
