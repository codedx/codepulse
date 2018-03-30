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

class MockIMetaDataAssemblyEmit : public IMetaDataAssemblyEmit
{
public:
	virtual ~MockIMetaDataAssemblyEmit() {}

	MOCK_METHOD2_WITH_CALLTYPE(__stdcall, QueryInterface, HRESULT(const IID& riid, void** ppvObject));

	MOCK_METHOD0_WITH_CALLTYPE(__stdcall, AddRef, ULONG(void));
	MOCK_METHOD0_WITH_CALLTYPE(__stdcall, Release, ULONG(void));

	MOCK_METHOD7_WITH_CALLTYPE(__stdcall, DefineAssembly, HRESULT(
		const void  *pbPublicKey,           // [IN] Public key of the assembly.
		ULONG       cbPublicKey,            // [IN] Count of bytes in the public key.
		ULONG       ulHashAlgId,            // [IN] Hash algorithm used to hash the files.
		LPCWSTR     szName,                 // [IN] Name of the assembly.
		const ASSEMBLYMETADATA *pMetaData,  // [IN] Assembly MetaData.
		DWORD       dwAssemblyFlags,        // [IN] Flags.
		mdAssembly  *pma));             // [OUT] Returned Assembly token.

	MOCK_METHOD8_WITH_CALLTYPE(__stdcall, DefineAssemblyRef, HRESULT(
		const void  *pbPublicKeyOrToken,    // [IN] Public key or token of the assembly.
		ULONG       cbPublicKeyOrToken,     // [IN] Count of bytes in the public key or token.
		LPCWSTR     szName,                 // [IN] Name of the assembly being referenced.
		const ASSEMBLYMETADATA *pMetaData,  // [IN] Assembly MetaData.
		const void  *pbHashValue,           // [IN] Hash Blob.
		ULONG       cbHashValue,            // [IN] Count of bytes in the Hash Blob.
		DWORD       dwAssemblyRefFlags,     // [IN] Flags.
		mdAssemblyRef *pmdar));         // [OUT] Returned AssemblyRef token.

	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, DefineFile, HRESULT(
		LPCWSTR     szName,                 // [IN] Name of the file.
		const void  *pbHashValue,           // [IN] Hash Blob.
		ULONG       cbHashValue,            // [IN] Count of bytes in the Hash Blob.
		DWORD       dwFileFlags,            // [IN] Flags.
		mdFile      *pmdf));            // [OUT] Returned File token.

	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, DefineExportedType, HRESULT(
		LPCWSTR     szName,                 // [IN] Name of the Com Type.
		mdToken     tkImplementation,       // [IN] mdFile or mdAssemblyRef or mdExportedType
		mdTypeDef   tkTypeDef,              // [IN] TypeDef token within the file.
		DWORD       dwExportedTypeFlags,    // [IN] Flags.
		mdExportedType   *pmdct));      // [OUT] Returned ExportedType token.

	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, DefineManifestResource, HRESULT(
		LPCWSTR     szName,                 // [IN] Name of the resource.
		mdToken     tkImplementation,       // [IN] mdFile or mdAssemblyRef that provides the resource.
		DWORD       dwOffset,               // [IN] Offset to the beginning of the resource within the file.
		DWORD       dwResourceFlags,        // [IN] Flags.
		mdManifestResource  *pmdmr));   // [OUT] Returned ManifestResource token.

	MOCK_METHOD7_WITH_CALLTYPE(__stdcall, SetAssemblyProps, HRESULT(
		mdAssembly  pma,                    // [IN] Assembly token.
		const void  *pbPublicKey,           // [IN] Public key of the assembly.
		ULONG       cbPublicKey,            // [IN] Count of bytes in the public key.
		ULONG       ulHashAlgId,            // [IN] Hash algorithm used to hash the files.
		LPCWSTR     szName,                 // [IN] Name of the assembly.
		const ASSEMBLYMETADATA *pMetaData,  // [IN] Assembly MetaData.
		DWORD       dwAssemblyFlags));  // [IN] Flags.

	MOCK_METHOD8_WITH_CALLTYPE(__stdcall, SetAssemblyRefProps, HRESULT(
		mdAssemblyRef ar,                   // [IN] AssemblyRefToken.
		const void  *pbPublicKeyOrToken,    // [IN] Public key or token of the assembly.
		ULONG       cbPublicKeyOrToken,     // [IN] Count of bytes in the public key or token.
		LPCWSTR     szName,                 // [IN] Name of the assembly being referenced.
		const ASSEMBLYMETADATA *pMetaData,  // [IN] Assembly MetaData.
		const void  *pbHashValue,           // [IN] Hash Blob.
		ULONG       cbHashValue,            // [IN] Count of bytes in the Hash Blob.
		DWORD       dwAssemblyRefFlags)); // [IN] Token for Execution Location.

	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, SetFileProps, HRESULT(
		mdFile      file,                   // [IN] File token.
		const void  *pbHashValue,           // [IN] Hash Blob.
		ULONG       cbHashValue,            // [IN] Count of bytes in the Hash Blob.
		DWORD       dwFileFlags));      // [IN] Flags.

	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, SetExportedTypeProps, HRESULT(
		mdExportedType   ct,                // [IN] ExportedType token.
		mdToken     tkImplementation,       // [IN] mdFile or mdAssemblyRef or mdExportedType.
		mdTypeDef   tkTypeDef,              // [IN] TypeDef token within the file.
		DWORD       dwExportedTypeFlags));   // [IN] Flags.

	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, SetManifestResourceProps, HRESULT(
		mdManifestResource  mr,             // [IN] ManifestResource token.
		mdToken     tkImplementation,       // [IN] mdFile or mdAssemblyRef that provides the resource.
		DWORD       dwOffset,               // [IN] Offset to the beginning of the resource within the file.
		DWORD       dwResourceFlags));  // [IN] Flags.
};