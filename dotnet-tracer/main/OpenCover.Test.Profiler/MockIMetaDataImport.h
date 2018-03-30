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

class MockIMetaDataImport : public IMetaDataImport {
public:
	virtual ~MockIMetaDataImport() {}

	MOCK_METHOD2_WITH_CALLTYPE(__stdcall, QueryInterface, HRESULT(const IID& riid, void** ppvObject));

	MOCK_METHOD0_WITH_CALLTYPE(__stdcall, AddRef, ULONG(void));
	MOCK_METHOD0_WITH_CALLTYPE(__stdcall, Release, ULONG(void));

	MOCK_METHOD1_WITH_CALLTYPE(__stdcall, CloseEnum,
		void(HCORENUM hEnum));
	MOCK_METHOD2_WITH_CALLTYPE(__stdcall, CountEnum,
		HRESULT(HCORENUM hEnum, ULONG *pulCount));
	MOCK_METHOD2_WITH_CALLTYPE(__stdcall, ResetEnum,
		HRESULT(HCORENUM hEnum, ULONG ulPos));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, EnumTypeDefs,
		HRESULT(HCORENUM *phEnum, mdTypeDef rTypeDefs[], ULONG cMax, ULONG *pcTypeDefs));
	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, EnumInterfaceImpls,
		HRESULT(HCORENUM *phEnum, mdTypeDef td, mdInterfaceImpl rImpls[], ULONG cMax, ULONG* pcImpls));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, EnumTypeRefs,
		HRESULT(HCORENUM *phEnum, mdTypeRef rTypeRefs[], ULONG cMax, ULONG* pcTypeRefs));
	MOCK_METHOD3_WITH_CALLTYPE(__stdcall, FindTypeDefByName,
		HRESULT(LPCWSTR szTypeDef, mdToken tkEnclosingClass, mdTypeDef *ptd));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, GetScopeProps,
		HRESULT(LPWSTR szName, ULONG cchName, ULONG *pchName, GUID *pmvid));
	MOCK_METHOD1_WITH_CALLTYPE(__stdcall, GetModuleFromScope,
		HRESULT(mdModule *pmd));
	MOCK_METHOD6_WITH_CALLTYPE(__stdcall, GetTypeDefProps,
		HRESULT(mdTypeDef td, LPWSTR szTypeDef, ULONG cchTypeDef, ULONG *pchTypeDef, DWORD *pdwTypeDefFlags, mdToken *ptkExtends));
	MOCK_METHOD3_WITH_CALLTYPE(__stdcall, GetInterfaceImplProps,
		HRESULT(mdInterfaceImpl iiImpl, mdTypeDef *pClass, mdToken *ptkIface));
	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, GetTypeRefProps,
		HRESULT(mdTypeRef tr, mdToken *ptkResolutionScope, LPWSTR szName, ULONG cchName, ULONG *pchName));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, ResolveTypeRef,
		HRESULT(mdTypeRef tr, REFIID riid, IUnknown **ppIScope, mdTypeDef *ptd));
	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, EnumMembers,
		HRESULT(HCORENUM *phEnum, mdTypeDef cl, mdToken rMembers[], ULONG cMax, ULONG *pcTokens));
	MOCK_METHOD6_WITH_CALLTYPE(__stdcall, EnumMembersWithName,
		HRESULT (HCORENUM *phEnum, mdTypeDef cl, LPCWSTR szName, mdToken rMembers[], ULONG cMax, ULONG *pcTokens));
	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, EnumMethods,
		HRESULT (HCORENUM *phEnum, mdTypeDef cl, mdMethodDef rMethods[], ULONG cMax, ULONG *pcTokens));
	MOCK_METHOD6_WITH_CALLTYPE(__stdcall, EnumMethodsWithName,
		HRESULT (HCORENUM *phEnum, mdTypeDef cl, LPCWSTR szName, mdMethodDef rMethods[], ULONG cMax, ULONG *pcTokens));
	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, EnumFields,
		HRESULT (HCORENUM *phEnum, mdTypeDef cl, mdFieldDef rFields[], ULONG cMax, ULONG *pcTokens));
	MOCK_METHOD6_WITH_CALLTYPE(__stdcall, EnumFieldsWithName,
		HRESULT (HCORENUM *phEnum, mdTypeDef cl, LPCWSTR szName, mdFieldDef rFields[], ULONG cMax, ULONG *pcTokens));
	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, EnumParams,
		HRESULT (HCORENUM *phEnum, mdMethodDef mb, mdParamDef rParams[], ULONG cMax, ULONG *pcTokens));
	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, EnumMemberRefs,
		HRESULT (HCORENUM *phEnum, mdToken tkParent, mdMemberRef rMemberRefs[], ULONG cMax, ULONG *pcTokens));
	MOCK_METHOD6_WITH_CALLTYPE(__stdcall, EnumMethodImpls,
		HRESULT (HCORENUM *phEnum, mdTypeDef td, mdToken rMethodBody[], mdToken rMethodDecl[], ULONG cMax, ULONG *pcTokens));
	MOCK_METHOD6_WITH_CALLTYPE(__stdcall, EnumPermissionSets,
		HRESULT (HCORENUM *phEnum, mdToken tk, DWORD dwActions, mdPermission rPermission[], ULONG cMax, ULONG *pcTokens));
	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, FindMember,
		HRESULT (mdTypeDef td, LPCWSTR szName, PCCOR_SIGNATURE pvSigBlob, ULONG cbSigBlob, mdToken *pmb));
	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, FindMethod,
		HRESULT (mdTypeDef td, LPCWSTR szName, PCCOR_SIGNATURE pvSigBlob, ULONG cbSigBlob, mdMethodDef *pmb));
	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, FindField,
		HRESULT (mdTypeDef td, LPCWSTR szName, PCCOR_SIGNATURE pvSigBlob, ULONG cbSigBlob, mdFieldDef *pmb));
	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, FindMemberRef,
		HRESULT (mdTypeRef td, LPCWSTR szName, PCCOR_SIGNATURE pvSigBlob, ULONG cbSigBlob, mdMemberRef *pmr));

	HRESULT __stdcall GetMethodProps(mdMethodDef mb, mdTypeDef *pClass, LPWSTR szMethod, ULONG cchMethod, ULONG *pchMethod, DWORD *pdwAttr, PCCOR_SIGNATURE *ppvSigBlob, ULONG *pcbSigBlob, ULONG *pulCodeRVA, DWORD *pdwImplFlags)
	{
		// NOTE: This method has more parameters than Google Mock supports.
		return E_FAIL;
	}

	MOCK_METHOD7_WITH_CALLTYPE(__stdcall, GetMemberRefProps,
		HRESULT(mdMemberRef mr, mdToken *ptk, LPWSTR szMember, ULONG cchMember, ULONG *pchMember, PCCOR_SIGNATURE *ppvSigBlob, ULONG *pbSig));
	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, EnumProperties,
		HRESULT(HCORENUM *phEnum, mdTypeDef td, mdProperty rProperties[], ULONG cMax, ULONG *pcProperties));
	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, EnumEvents,
		HRESULT(HCORENUM *phEnum, mdTypeDef td, mdEvent rEvents[], ULONG cMax, ULONG *pcEvents));
	
	HRESULT __stdcall GetEventProps(mdEvent ev, mdTypeDef *pClass, LPCWSTR szEvent, ULONG cchEvent, ULONG *pchEvent, DWORD *pdwEventFlags, mdToken *ptkEventType, mdMethodDef *pmdAddOn, mdMethodDef *pmdRemoveOn, mdMethodDef *pmdFire, mdMethodDef rmdOtherMethod[], ULONG cMax, ULONG *pcOtherMethod)
	{
		// NOTE: This method has more parameters than Google Mock supports.
		return E_FAIL;
	}
	
	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, EnumMethodSemantics,
		HRESULT(HCORENUM *phEnum, mdMethodDef mb, mdToken rEventProp[], ULONG cMax, ULONG *pcEventProp));
	MOCK_METHOD3_WITH_CALLTYPE(__stdcall, GetMethodSemantics,
		HRESULT(mdMethodDef mb, mdToken tkEventProp, DWORD *pdwSemanticsFlags));
	MOCK_METHOD6_WITH_CALLTYPE(__stdcall, GetClassLayout,
		HRESULT(mdTypeDef td, DWORD *pdwPackSize, COR_FIELD_OFFSET rFieldOffset[], ULONG cMax, ULONG *pcFieldOffset, ULONG *pulClassSize));
	MOCK_METHOD3_WITH_CALLTYPE(__stdcall, GetFieldMarshal,
		HRESULT(mdToken tk, PCCOR_SIGNATURE *ppvNativeType, ULONG *pcbNativeType));
	MOCK_METHOD3_WITH_CALLTYPE(__stdcall, GetRVA,
		HRESULT(mdToken tk, ULONG *pulCodeRVA, DWORD *pdwImplFlags));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, GetPermissionSetProps,
		HRESULT(mdPermission pm, DWORD *pdwAction, void const **ppvPermission, ULONG *pcbPermission));
	MOCK_METHOD3_WITH_CALLTYPE(__stdcall, GetSigFromToken,
		HRESULT(mdSignature mdSig, PCCOR_SIGNATURE *ppvSig, ULONG *pcbSig));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, GetModuleRefProps,
		HRESULT(mdModuleRef mur, LPWSTR szName, ULONG cchName, ULONG *pchName));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, EnumModuleRefs,
		HRESULT(HCORENUM *phEnum, mdModuleRef rModuleRefs[], ULONG cmax, ULONG *pcModuleRefs));
	MOCK_METHOD3_WITH_CALLTYPE(__stdcall, GetTypeSpecFromToken,
		HRESULT(mdTypeSpec typespec, PCCOR_SIGNATURE *ppvSig, ULONG *pcbSig));
	MOCK_METHOD2_WITH_CALLTYPE(__stdcall, GetNameFromToken,
		HRESULT(mdToken tk, MDUTF8CSTR *pszUtf8NamePtr));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, EnumUnresolvedMethods,
		HRESULT(HCORENUM *phEnum, mdToken rMethods[], ULONG cMax, ULONG *pcTokens));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, GetUserString,
		HRESULT(mdString stk, LPWSTR szString, ULONG cchString, ULONG *pchString));
	MOCK_METHOD6_WITH_CALLTYPE(__stdcall, GetPinvokeMap,
		HRESULT(mdToken tk, DWORD *pdwMappingFlags, LPWSTR szImportName, ULONG cchImportName, ULONG *pchImportName, mdModuleRef *pmrImportDLL));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, EnumSignatures,
		HRESULT(HCORENUM *phEnum, mdSignature rSignatures[], ULONG cmax, ULONG *pcSignatures));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, EnumTypeSpecs,
		HRESULT(HCORENUM *phEnum, mdTypeSpec rTypeSpecs[], ULONG cmax, ULONG *pcTypeSpecs));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, EnumUserStrings,
		HRESULT(HCORENUM *phEnum, mdString rStrings[], ULONG cmax, ULONG *pcStrings));
	MOCK_METHOD3_WITH_CALLTYPE(__stdcall, GetParamForMethodIndex,
		HRESULT(mdMethodDef md, ULONG ulParamSeq, mdParamDef *ppd));
	MOCK_METHOD6_WITH_CALLTYPE(__stdcall, EnumCustomAttributes,
		HRESULT(HCORENUM *phEnum, mdToken tk, mdToken tkType, mdCustomAttribute rCustomAttributes[], ULONG cMax, ULONG *pcCustomAttributes));
	MOCK_METHOD5_WITH_CALLTYPE(__stdcall, GetCustomAttributeProps,
		HRESULT(mdCustomAttribute cv, mdToken *ptkObj, mdToken *ptkType, void const **ppBlob, ULONG *pcbSize));
	MOCK_METHOD3_WITH_CALLTYPE(__stdcall, FindTypeRef,
		HRESULT(mdToken tkResolutionScope, LPCWSTR szName, mdTypeRef *ptr));
	
	HRESULT __stdcall GetMemberProps(mdToken mb, mdTypeDef *pClass, LPWSTR szMember, ULONG cchMember, ULONG *pchMember, DWORD *pdwAttr, PCCOR_SIGNATURE *ppvSigBlob, ULONG *pcbSigBlob, ULONG *pulCodeRVA, DWORD *pdwImplFlags, DWORD *pdwCPlusTypeFlag, UVCP_CONSTANT *ppValue, ULONG *pcchValue)
	{
		// NOTE: This method has more parameters than Google Mock supports.
		return E_FAIL;
	}
	
	HRESULT __stdcall GetFieldProps(mdFieldDef mb, mdTypeDef *pClass, LPWSTR szField, ULONG cchField, ULONG *pchField, DWORD *pdwAttr, PCCOR_SIGNATURE *ppvSigBlob, ULONG *pcbSigBlob, DWORD *pdwCPlusTypeFlag, UVCP_CONSTANT *ppValue, ULONG *pcchValue)
	{
		// NOTE: This method has more parameters than Google Mock supports.
		return E_FAIL;
	}

	HRESULT __stdcall GetPropertyProps(mdProperty prop, mdTypeDef *pClass, LPCWSTR szProperty, ULONG cchProperty, ULONG *pchProperty, DWORD *pdwPropFlags, PCCOR_SIGNATURE *ppvSig, ULONG *pbSig, DWORD *pdwCPlusTypeFlag, UVCP_CONSTANT *ppDefaultValue, ULONG *pcchDefaultValue, mdMethodDef *pmdSetter, mdMethodDef *pmdGetter, mdMethodDef rmdOtherMethod[], ULONG cMax, ULONG *pcOtherMethod)
	{
		// NOTE: This method has more parameters than Google Mock supports.
		return E_FAIL;
	}

	MOCK_METHOD10_WITH_CALLTYPE(__stdcall, GetParamProps, HRESULT(mdParamDef tk, mdMethodDef *pmd, ULONG *pulSequence, LPWSTR szName, ULONG cchName, ULONG *pchName, DWORD *pdwAttr, DWORD *pdwCPlusTypeFlag, UVCP_CONSTANT *ppValue, ULONG *pcchValue));
	MOCK_METHOD4_WITH_CALLTYPE(__stdcall, GetCustomAttributeByName,
		HRESULT(mdToken tkObj, LPCWSTR szName, const void **ppData, ULONG *pcbData));
	MOCK_METHOD1_WITH_CALLTYPE(__stdcall, IsValidToken,
		BOOL(mdToken tk));
	MOCK_METHOD2_WITH_CALLTYPE(__stdcall, GetNestedClassProps,
		HRESULT(mdTypeDef tdNestedClass, mdTypeDef *ptdEnclosingClass));
	MOCK_METHOD3_WITH_CALLTYPE(__stdcall, GetNativeCallConvFromSig,
		HRESULT(void const *pvSig, ULONG cbSig, ULONG *pCallConv));
	MOCK_METHOD2_WITH_CALLTYPE(__stdcall, IsGlobal,
		HRESULT(mdToken pd, int *pbGlobal));
};
