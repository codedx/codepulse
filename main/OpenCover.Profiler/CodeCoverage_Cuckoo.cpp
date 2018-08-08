//
// Modified work Copyright 2017 Secure Decisions, a division of Applied Visions, Inc.
//

#include "stdafx.h"
#include <tchar.h>

#include "CodeCoverage.h"

#define CUCKOO_SAFE_METHOD_NAME L"SafeVisited"
#define CUCKOO_CRITICAL_METHOD_NAME L"VisitedCritical"
#define CUCKOO_NEST_TYPE_NAME L"System.CannotUnloadAppDomainException"

static COR_SIGNATURE visitedMethodSafeCallSignature[] =
{
	IMAGE_CEE_CS_CALLCONV_DEFAULT,
	0x01,
	ELEMENT_TYPE_VOID,
	ELEMENT_TYPE_I4
};

static COR_SIGNATURE visitedMethodCriticalCallSignature[] =
{
	IMAGE_CEE_CS_CALLCONV_DEFAULT,
	0x03,
	ELEMENT_TYPE_VOID,
	ELEMENT_TYPE_I4,
	ELEMENT_TYPE_U8,
	ELEMENT_TYPE_U8
};

static COR_SIGNATURE ctorCallSignature[] =
{
	IMAGE_CEE_CS_CALLCONV_DEFAULT | IMAGE_CEE_CS_CALLCONV_HASTHIS,
	0x00,
	ELEMENT_TYPE_VOID
};

using namespace Instrumentation;

HRESULT CCodeCoverage::RegisterCuckoos(ModuleID moduleId){

	CComPtr<IMetaDataEmit> metaDataEmit;
	COM_FAIL_MSG_RETURN_ERROR(m_profilerInfo->GetModuleMetaData(moduleId,
		ofRead | ofWrite, IID_IMetaDataEmit, (IUnknown**)&metaDataEmit),
		_T("    ::ModuleLoadFinished(...) => GetModuleMetaData => 0x%X"));
	if (metaDataEmit == NULL) return S_OK;

	CComPtr<IMetaDataImport> metaDataImport;
	COM_FAIL_MSG_RETURN_ERROR(m_profilerInfo->GetModuleMetaData(moduleId,
		ofRead | ofWrite, IID_IMetaDataImport, (IUnknown**)&metaDataImport),
		_T("    ::ModuleLoadFinished(...) => GetModuleMetaData => 0x%X"));
	if (metaDataImport == NULL) return S_OK;

	mdTypeDef systemObject = mdTokenNil;
	if (S_OK == metaDataImport->FindTypeDefByName(L"System.Object", mdTokenNil, &systemObject))
	{
		#ifdef TRACE_ENABLED
		RELTRACE(_T("::ModuleLoadFinished(...) => Adding methods to mscorlib..."));
		#endif
		mdMethodDef systemObjectCtor;
		COM_FAIL_MSG_RETURN_ERROR(metaDataImport->FindMethod(systemObject, L".ctor",
			ctorCallSignature, sizeof(ctorCallSignature), &systemObjectCtor),
			_T("    ::ModuleLoadFinished(...) => FindMethod => 0x%X)"));

		ULONG ulCodeRVA = 0;
		COM_FAIL_MSG_RETURN_ERROR(metaDataImport->GetMethodProps(systemObjectCtor, NULL, NULL,
			0, NULL, NULL, NULL, NULL, &ulCodeRVA, NULL),
			_T("    ::ModuleLoadFinished(...) => GetMethodProps => 0x%X"));

		mdCustomAttribute customAttr;
		mdToken attributeCtor;
		mdTypeDef attributeTypeDef;
		mdTypeDef nestToken;

		COM_FAIL_MSG_RETURN_ERROR(metaDataImport->FindTypeDefByName(CUCKOO_NEST_TYPE_NAME, mdTokenNil, &nestToken),
			_T("    ::ModuleLoadFinished(...) => FindTypeDefByName => 0x%X"));

		// create a method that we will mark up with the SecurityCriticalAttribute
		COM_FAIL_MSG_RETURN_ERROR(metaDataEmit->DefineMethod(nestToken, CUCKOO_CRITICAL_METHOD_NAME,
			mdPublic | mdStatic | mdHideBySig, visitedMethodCriticalCallSignature, sizeof(visitedMethodCriticalCallSignature),
			ulCodeRVA, miIL | miManaged | miPreserveSig | miNoInlining, &m_cuckooCriticalToken),
			_T("    ::ModuleLoadFinished(...) => DefineMethod => 0x%X"));

		COM_FAIL_MSG_RETURN_ERROR(metaDataImport->FindTypeDefByName(L"System.Security.SecurityCriticalAttribute",
			NULL, &attributeTypeDef), _T("    :ModuleLoadFinished(...) => FindTypeDefByName => 0x%X"));

		if (m_runtimeType == COR_PRF_DESKTOP_CLR)
		{
			// for desktop we use the .ctor that takes a SecurityCriticalScope argument as the 
			// default (no arguments) constructor fails with "0x801311C2 - known custom attribute value is bad" 
			// when we try to attach it in .NET2 - .NET4 version doesn't care which one we use
			mdTypeDef scopeToken;
			COM_FAIL_MSG_RETURN_ERROR(metaDataImport->FindTypeDefByName(L"System.Security.SecurityCriticalScope", mdTokenNil, &scopeToken),
				_T("    ::ModuleLoadFinished(...) => FindTypeDefByName => 0x%X"));

			ULONG sigLength = 4;
			COR_SIGNATURE ctorCallSignatureEnum[] =
			{
				IMAGE_CEE_CS_CALLCONV_DEFAULT | IMAGE_CEE_CS_CALLCONV_HASTHIS,
				0x01,
				ELEMENT_TYPE_VOID,
				ELEMENT_TYPE_VALUETYPE,
				0x00, 0x00, 0x00, 0x00 // make room for our compressed token - should always be 2 but...
			};

			sigLength += CorSigCompressToken(scopeToken, &ctorCallSignatureEnum[4]);

			COM_FAIL_MSG_RETURN_ERROR(metaDataImport->FindMember(attributeTypeDef,
				L".ctor", ctorCallSignatureEnum, sigLength, &attributeCtor),
				_T("    ::ModuleLoadFinished(...) => FindMember => 0x%X"));

			unsigned char blob[] = { 0x01, 0x00, 0x01, 0x00, 0x00, 0x00 }; // prolog U2 plus an enum of I4 (little-endian)
			COM_FAIL_MSG_RETURN_ERROR(metaDataEmit->DefineCustomAttribute(m_cuckooCriticalToken, attributeCtor, blob, sizeof(blob), &customAttr),
				_T("    ::ModuleLoadFinished(...) => DefineCustomAttribute => 0x%X"));
		}
		else
		{
			// silverlight only has one .ctor for this type
			COM_FAIL_MSG_RETURN_ERROR(metaDataImport->FindMember(attributeTypeDef,
				L".ctor", ctorCallSignature, sizeof(ctorCallSignature), &attributeCtor),
				_T("    ::ModuleLoadFinished(...) => FindMember => 0x%X"));

			COM_FAIL_MSG_RETURN_ERROR(metaDataEmit->DefineCustomAttribute(m_cuckooCriticalToken, attributeCtor, NULL, 0, &customAttr),
				_T("    ::ModuleLoadFinished(...) => DefineCustomAttribute => 0x%X"));
		}

		// create a method that we will mark up with the SecuritySafeCriticalAttribute
		COM_FAIL_MSG_RETURN_ERROR(metaDataEmit->DefineMethod(nestToken, CUCKOO_SAFE_METHOD_NAME,
			mdPublic | mdStatic | mdHideBySig, visitedMethodSafeCallSignature, sizeof(visitedMethodSafeCallSignature),
			ulCodeRVA, miIL | miManaged | miPreserveSig | miNoInlining, &m_cuckooSafeToken),
			_T("    ::ModuleLoadFinished(...) => DefineMethod => 0x%X"));

		COM_FAIL_MSG_RETURN_ERROR(metaDataImport->FindTypeDefByName(L"System.Security.SecuritySafeCriticalAttribute",
			NULL, &attributeTypeDef),
			_T("    ::ModuleLoadFinished(...) => FindTypeDefByName => 0x%X"));

		COM_FAIL_MSG_RETURN_ERROR(metaDataImport->FindMember(attributeTypeDef,
			L".ctor", ctorCallSignature, sizeof(ctorCallSignature), &attributeCtor),
			_T("    ::ModuleLoadFinished(...) => FindMember => 0x%X"));

		COM_FAIL_MSG_RETURN_ERROR(metaDataEmit->DefineCustomAttribute(m_cuckooSafeToken, attributeCtor, NULL, 0, &customAttr),
			_T("    ::ModuleLoadFinished(...) => DefineCustomAttribute => 0x%X"));

		m_traceContainerBase = std::make_shared<Context::TraceContainerBase>(m_profilerInfo, m_assemblyRegistry, m_cuckooSafeToken);
		m_traceContainerCallContext = std::make_unique<Context::TraceContainerCallContext>(m_profilerInfo, m_assemblyRegistry, m_traceContainerBase);
		m_httpApplication = std::make_unique<Context::HttpApplication>(m_profilerInfo, m_assemblyRegistry);

		#ifdef TRACE_ENABLED
		RELTRACE(_T("::ModuleLoadFinished(...) => Added methods to mscorlib"));
		#endif
	}

	return S_OK;
}

mdMemberRef CCodeCoverage::RegisterSafeCuckooMethod(ModuleID moduleId, const WCHAR* moduleName)
{
	#ifdef TRACE_ENABLED
	ATLTRACE(_T("::RegisterSafeCuckooMethod(%X) => %s"), moduleId, CUCKOO_SAFE_METHOD_NAME);
	#endif

	// for modules we are going to instrument add our reference to the method marked 
	// with the SecuritySafeCriticalAttribute
	CComPtr<IMetaDataEmit> metaDataEmit;
	COM_FAIL_MSG_RETURN_ERROR(m_profilerInfo->GetModuleMetaData(moduleId,
		ofRead | ofWrite, IID_IMetaDataEmit, (IUnknown**)&metaDataEmit),
		_T("    ::RegisterSafeCuckooMethod(...) => GetModuleMetaData => 0x%X"));

	mdModuleRef mscorlibRef;
	COM_FAIL_MSG_RETURN_ERROR(GetModuleRef(moduleId, moduleName, mscorlibRef),
		_T("    ::RegisterSafeCuckooMethod(...) => GetModuleRef => 0x%X"));

	mdTypeDef nestToken;
	COM_FAIL_MSG_RETURN_ERROR(metaDataEmit->DefineTypeRefByName(mscorlibRef, CUCKOO_NEST_TYPE_NAME, &nestToken),
		_T("    ::RegisterSafeCuckooMethod(...) => DefineTypeRefByName => 0x%X"));

	mdMemberRef cuckooSafeToken;
	COM_FAIL_MSG_RETURN_ERROR(metaDataEmit->DefineMemberRef(nestToken, CUCKOO_SAFE_METHOD_NAME,
		visitedMethodSafeCallSignature, sizeof(visitedMethodSafeCallSignature), &cuckooSafeToken),
		_T("    ::RegisterSafeCuckooMethod(...) => DefineMemberRef => 0x%X"));

	return cuckooSafeToken;
}

/// <summary>This is the method marked with the SecurityCriticalAttribute</summary>
/// <remarks>This method makes the call into the profiler</remarks>
HRESULT CCodeCoverage::AddCriticalCuckooBody(ModuleID moduleId)
{
	#ifdef TRACE_ENABLED
	ATLTRACE(_T("::AddCriticalCuckooBody => Adding VisitedCritical..."));
	#endif

	mdSignature pvsig = GetMethodSignatureToken_I4U8U8(moduleId);
	void(__fastcall *pt)(ULONG, ULONGLONG, ULONGLONG) = GetInstrumentPointVisitWithContext();

	InstructionList instructions;
	instructions.push_back(new Instruction(CEE_LDARG_0));
	instructions.push_back(new Instruction(CEE_LDARG_1));
	instructions.push_back(new Instruction(CEE_LDARG_2));
#ifdef _WIN64
	instructions.push_back(new Instruction(CEE_LDC_I8, (ULONGLONG)pt));
#else
	instructions.push_back(new Instruction(CEE_LDC_I4, (ULONG)pt));
#endif
	instructions.push_back(new Instruction(CEE_CALLI, pvsig));

	InstrumentMethodWith(moduleId, m_cuckooCriticalToken, instructions);

	#ifdef TRACE_ENABLED
	ATLTRACE(_T("::AddCriticalCuckooBody => Adding VisitedCritical - Done!"));
	#endif

	return S_OK;
}

HRESULT CCodeCoverage::AddSafeCuckooBody(ModuleID moduleId)
{
	if (m_traceContainerCallContext->IsRegistered())
	{
		return AddTraceSafeCuckooBody(moduleId);
	}
	
	#ifdef TRACE_ENABLED
	ATLTRACE(_T("::AddSafeCuckooBody => Adding SafeVisited..."));
	#endif

	InstructionList instructions;
	instructions.push_back(new Instruction(CEE_LDARG_0));
	instructions.push_back(new Instruction(CEE_LDC_I4, 0));
	instructions.push_back(new Instruction(CEE_CONV_U8));
	instructions.push_back(new Instruction(CEE_LDC_I4, 0));
	instructions.push_back(new Instruction(CEE_CONV_U8));
	instructions.push_back(new Instruction(CEE_CALL, m_cuckooCriticalToken));

	InstrumentMethodWith(moduleId, m_cuckooSafeToken, instructions);

	#ifdef TRACE_ENABLED
	ATLTRACE(_T("::AddSafeCuckooBody => Adding SafeVisited - Done!"));
	#endif

	return S_OK;
}

HRESULT CCodeCoverage::CuckooSupportCompilation(
	AssemblyID assemblyId,
	mdToken functionToken,
	ModuleID moduleId)
{
    // early escape if token is not one we want
    if ((m_cuckooCriticalToken != functionToken) && (m_cuckooSafeToken != functionToken))
        return S_OK;

	auto assemblyName = GetAssemblyName(assemblyId);
	// check that we have the right module
	if (MSCORLIB_NAME == assemblyName || DNCORLIB_NAME == assemblyName) 
	{
		if (m_cuckooCriticalToken == functionToken)
		{
			COM_FAIL_MSG_RETURN_ERROR(AddCriticalCuckooBody(moduleId),
				_T("    ::JITCompilationStarted(...) => AddCriticalCuckooBody => 0x%X"));
		}

		if (m_cuckooSafeToken == functionToken)
		{
			COM_FAIL_MSG_RETURN_ERROR(AddSafeCuckooBody(moduleId),
				_T("    ::JITCompilationStarted(...) => AddSafeCuckooBody => 0x%X"));
		}
	}
	return S_OK;
}