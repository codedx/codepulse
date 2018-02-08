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

#include <cor.h>
#include <gmock/gmock.h>

class MockIMetaDataAssemblyImport : public IMetaDataAssemblyImport {
public:
	virtual ~MockIMetaDataAssemblyImport() {}

	MOCK_METHOD2_WITH_CALLTYPE(__stdcall, QueryInterface, HRESULT(const IID& riid, void** ppvObject));

	MOCK_METHOD0_WITH_CALLTYPE(__stdcall, AddRef, ULONG(void));
	MOCK_METHOD0_WITH_CALLTYPE(__stdcall, Release, ULONG(void));

	MOCK_METHOD9_WITH_CALLTYPE(__stdcall, GetAssemblyProps,
		HRESULT(mdAssembly mda, const void **ppbPublicKey, ULONG *pcbPublicKey, ULONG *pulHashAlgId, LPWSTR szName, ULONG cchName, ULONG *pchName, ASSEMBLYMETADATA *pMetaData, DWORD *pdwAssemblyFlags));
	MOCK_METHOD10_WITH_CALLTYPE(__stdcall, GetAssemblyRefProps,
		HRESULT(mdAssemblyRef mdar, const void **ppbPublicKeyOrToken, ULONG *pcbPublicKeyOrToken, LPWSTR szName, ULONG cchName, ULONG *pchName, ASSEMBLYMETADATA *pMetaData, const void **ppbHashValue, ULONG *pcbHashValue, DWORD *pdwAssemblyRefFlags));
	MOCK_METHOD7_WITH_CALLTYPE(__stdcall, GetFileProps,
		HRESULT(mdFile mdf, LPWSTR szName, ULONG cchName, ULONG *pchName, const void **ppbHashValue, ULONG *pcbHashValue, DWORD *pdwFileFlags));
	MOCK_METHOD7_WITH_CALLTYPE(__stdcall, GetExportedTypeProps,
		HRESULT(mdExportedType mdct, LPWSTR szName, ULONG cchName, ULONG *pchName, mdToken *ptkImplementation, mdTypeDef *ptkTypeDef, DWORD *pdwExportedTypeFlags));
	MOCK_METHOD7_WITH_CALLTYPE(__stdcall, GetManifestResourceProps,
		HRESULT(mdManifestResource mdmr, LPWSTR szName, ULONG cchName, ULONG *pchName, mdToken *ptkImplementation, DWORD *pdwOffset, DWORD *pdwResourceFlags));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, EnumAssemblyRefs,
		HRESULT(HCORENUM *phEnum, mdAssemblyRef rAssemblyRefs[], ULONG cMax, ULONG *pcTokens));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, EnumFiles,
		HRESULT(HCORENUM *phEnum, mdFile rFiles[], ULONG cMax, ULONG *pcTokens));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, EnumExportedTypes,
		HRESULT(HCORENUM *phEnum, mdExportedType rExportedTypes[], ULONG cMax, ULONG *pcTokens));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, EnumManifestResources,
		HRESULT(HCORENUM *phEnum, mdManifestResource rManifestResources[], ULONG cMax, ULONG *pcTokens));
	MOCK_METHOD1_WITH_CALLTYPE(__stdcall, GetAssemblyFromScope,
		HRESULT(mdAssembly *ptkAssembly));
	MOCK_METHOD3_WITH_CALLTYPE(__stdcall, FindExportedTypeByName,
		HRESULT(LPCWSTR szName, mdToken mdtExportedType, mdExportedType *ptkExportedType));
	MOCK_METHOD2_WITH_CALLTYPE(__stdcall, FindManifestResourceByName,
		HRESULT(LPCWSTR szName, mdManifestResource *ptkManifestResource));
	MOCK_METHOD1_WITH_CALLTYPE(__stdcall, CloseEnum,
		void (HCORENUM hEnum));
	MOCK_METHOD6_WITH_CALLTYPE(__stdcall, FindAssembliesByName,
		HRESULT(LPCWSTR szAppBase, LPCWSTR szPrivateBin, LPCWSTR szAssemblyName, IUnknown *ppIUnk[], ULONG cMax, ULONG *pcAssemblies));
};