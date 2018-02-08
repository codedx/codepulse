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

#include "stdafx.h"
#include <sstream>
#include <algorithm>

#include "AssemblyRegistry.h"
#include "PublicKeyTokenCreator.h"

namespace Injection
{
	AssemblyRegistry::AssemblyRegistry(const ATL::CComPtr<ICorProfilerInfo>& profilerInfo)
		: m_profilerInfo(profilerInfo)
	{
	}

	bool AssemblyRegistry::FillAssembliesByName(const std::wstring& name, std::vector<AssemblyReference>& referencedAssemblies) const
	{
		const auto knownAssemblyReferences = m_assemblyVersionRegistry.find(name);
		if (knownAssemblyReferences == m_assemblyVersionRegistry.end())
		{
			return false;
		}

		auto iter = knownAssemblyReferences->second.begin();
		while (iter != knownAssemblyReferences->second.end())
		{
			referencedAssemblies.push_back(*iter);
			++iter;
		}
		return referencedAssemblies.size() > 0;
	}

	bool AssemblyRegistry::FindMaxAssemblyVersion(const std::wstring& name, AssemblyVersion& assemblyVersion) const
	{
		AssemblyReference assemblyReference;
		if (!FindMaxAssemblyVersion(name, assemblyReference))
		{
			return false;
		}
		assemblyVersion = assemblyReference.version;
		return true;
	}

	bool AssemblyRegistry::FindMaxAssemblyVersion(const std::wstring& name, AssemblyReference& assemblyReference) const
	{
		std::vector<AssemblyReference> referencedAssemblies;
		if (!FillAssembliesByName(name, referencedAssemblies))
		{
			return false;
		}

		auto& reference = referencedAssemblies[0];
		for (auto iter : referencedAssemblies)
		{
			if (reference.version >= iter.version) continue;
			reference = iter;
		}

		assemblyReference = reference;
		return true;
	}

	HRESULT AssemblyRegistry::RecordAssemblyMetadataForModule(const ModuleID moduleId)
	{
		ATL::CComPtr<IMetaDataAssemblyImport> metaDataAssemblyImport;
		HRESULT result = m_profilerInfo->GetModuleMetaData(moduleId,
			ofRead | ofWrite, IID_IMetaDataAssemblyImport, reinterpret_cast<IUnknown**>(&metaDataAssemblyImport));

		if (!SUCCEEDED(result))
		{
			return result;
		}

		if (metaDataAssemblyImport == nullptr) return E_FAIL;

		unsigned char* pbPublicKeyOrToken;
		ULONG cbPublicKeyOrToken = 0;
		ULONG hashAlgId = 0;
		const ULONG chName = 250;
		WCHAR zName[chName];
		ULONG chNameOut = 0;
		ASSEMBLYMETADATA metaData;
		DWORD assemblyRefFlags = 0;

		ZeroMemory(zName, sizeof(WCHAR) * 250);
		ZeroMemory(&metaData, sizeof(ASSEMBLYMETADATA));

		auto assembly = mdAssemblyNil;
		result = metaDataAssemblyImport->GetAssemblyFromScope(&assembly);
		if (!SUCCEEDED(result))
		{
			return result;
		}

		result = metaDataAssemblyImport->GetAssemblyProps(assembly,
			(const void**)&pbPublicKeyOrToken,
			&cbPublicKeyOrToken,
			&hashAlgId,
			zName,
			chName,
			&chNameOut,
			&metaData,
			&assemblyRefFlags);

		if (!SUCCEEDED(result))
		{
			return result;
		}

		try
		{
			StoreAssemblyDetails(pbPublicKeyOrToken, cbPublicKeyOrToken, hashAlgId, zName, chNameOut, metaData, assemblyRefFlags);
		}
		catch (std::runtime_error)
		{
			return E_UNEXPECTED;
		}

		HCORENUM hEnum = nullptr;
		mdAssemblyRef references[20];

		result = S_OK;
		while (result == S_OK)
		{
			ZeroMemory(zName, sizeof(WCHAR) * 250);
			ZeroMemory(&metaData, sizeof(ASSEMBLYMETADATA));
			ZeroMemory(references, 20 * sizeof(mdAssemblyRef));

			ULONG refCount = 0;
			result = metaDataAssemblyImport->EnumAssemblyRefs(&hEnum,
				references,
				20,
				&refCount);

			if (!SUCCEEDED(result)) continue;

			for (auto i = 0ul; i < refCount; i++)
			{
				const auto propsResult = metaDataAssemblyImport->GetAssemblyRefProps(references[i],
					(const void**)&pbPublicKeyOrToken,
					&cbPublicKeyOrToken,
					zName,
					chName,
					&chNameOut,
					&metaData,
					nullptr,
					nullptr,
					&assemblyRefFlags);

				if (propsResult != S_OK)
				{
					metaDataAssemblyImport->CloseEnum(hEnum);
					return propsResult;
				}

				try
				{
					StoreAssemblyDetails(pbPublicKeyOrToken, cbPublicKeyOrToken, CALG_SHA1, zName, chNameOut, metaData, assemblyRefFlags);
				}
				catch (std::runtime_error)
				{
					return E_UNEXPECTED;
				}
			}
		}
		metaDataAssemblyImport->CloseEnum(hEnum);

		return S_OK;
	}

	bool AssemblyRegistry::StoreAssemblyDetails(unsigned char* pbPublicKeyOrToken,
		const ULONG cbPublicKeyOrToken,
		const ALG_ID hashAlgId,
		wchar_t* pzName,
		const ULONG chNameOut,
		const ASSEMBLYMETADATA& metaData,
		const DWORD flags)
	{
		std::wstringstream assemblyNameStream;
		for (auto j = 0ul; j < chNameOut; j++)
		{
			if (pzName[j] == NULL)
				break;

			assemblyNameStream << pzName[j];
		}

		AssemblyReference assemblyReference;
		assemblyReference.version.majorVersion = metaData.usMajorVersion;
		assemblyReference.version.minorVersion = metaData.usMinorVersion;
		assemblyReference.version.buildNumber = metaData.usBuildNumber;
		assemblyReference.version.revisionNumber = metaData.usRevisionNumber;
		assemblyReference.name = std::wstring(assemblyNameStream.str());

		ZeroMemory(assemblyReference.publicKeyToken, sizeof(assemblyReference.publicKeyToken));

		const auto sizeOfPublicKeyToken = 8ul;
		if (cbPublicKeyOrToken == sizeOfPublicKeyToken)
		{
			memcpy(assemblyReference.publicKeyToken, pbPublicKeyOrToken, cbPublicKeyOrToken);
		}
		else
		{
			std::vector<BYTE> publicKeyToken;
			PublicKeyTokenCreator tokenCreator;
			if (!tokenCreator.GetPublicKeyToken(pbPublicKeyOrToken, cbPublicKeyOrToken, hashAlgId, publicKeyToken))
			{
				throw std::runtime_error("Unable to compute public key token");
			}
			copy(publicKeyToken.begin(), publicKeyToken.end(), assemblyReference.publicKeyToken);
		}

		auto knownAssemblyReference = m_assemblyVersionRegistry.find(assemblyReference.name);
		if (knownAssemblyReference == m_assemblyVersionRegistry.end())
		{
			std::vector<AssemblyReference> assemblyVersions;
			assemblyVersions.push_back(assemblyReference);
			m_assemblyVersionRegistry.insert(make_pair(assemblyReference.name, assemblyVersions));
			return true;
		}

		const auto knownAssemblyVersion = find(knownAssemblyReference->second.begin(), knownAssemblyReference->second.end(), assemblyReference);
		if (knownAssemblyVersion == knownAssemblyReference->second.end())
		{
			knownAssemblyReference->second.push_back(assemblyReference);
		}
		return false;
	}
}