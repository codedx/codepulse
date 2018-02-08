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

#include "HttpApplication.h"
#include "AssemblyRegistry.h"

using namespace Instrumentation;
using namespace Injection;

namespace Context
{
	HttpApplication::HttpApplication(const ATL::CComPtr<ICorProfilerInfo>& profilerInfo,
		const std::shared_ptr<Injection::AssemblyRegistry>& assemblyRegistry) :
		InjectedType(profilerInfo, assemblyRegistry),
		m_typeDef(mdTypeDefNil),
		m_initMethodDef(mdMethodDefNil),
		m_onTraceContainerBeginRequestMethodDef(mdMethodDefNil),
		m_onTraceContainerEndRequestMethodDef(mdMethodDefNil),
		m_traceContainerGetCurrentRef(mdMemberRefNil),
		m_addBeginRequestDef(mdTokenNil),
		m_addEndRequestDef(mdTokenNil),
		m_eventHandlerCtorRef(mdMemberRefNil),
		m_traceContainerBaseNotifyContextEndRef(mdMemberRefNil),
		m_contextIdKey(mdStringNil),
		m_httpApplicationTypeDef(mdTypeDefNil),
		m_httpApplicationGetContext(mdTokenNil),
		m_guidTypeRef(mdTypeRefNil),
		m_guidParseRef(mdMemberRefNil),
		m_httpContextGetItems(mdTokenNil),
		m_traceContainerBaseContextIdRef(mdMemberRefNil),
		m_traceContainerBaseSetContextIdRef(mdMemberRefNil),
		m_objectToStringRef(mdMemberRefNil),
		m_dictionaryGetItem(mdMemberRefNil),
		m_dictionarySetItem(mdMemberRefNil),
		m_endRequestLocalVariablesSignature(mdSignatureNil)
	{
		
	}

	bool HttpApplication::ShouldRegisterType(const ModuleID moduleId) const
	{
		if (!HasTypeDef(moduleId, L"System.Web.HttpApplication"))
		{
			return false;
		}

		AssemblyReference mscorlibReference;
		if (m_assemblyRegistry->FindMaxAssemblyVersion(L"mscorlib", mscorlibReference))
		{
			// EndRequestEvent available since .NET 1.1
			AssemblyVersion minimumDotNetVersion;
			minimumDotNetVersion.majorVersion = 1;
			minimumDotNetVersion.minorVersion = 1;
			minimumDotNetVersion.buildNumber = 0;
			minimumDotNetVersion.revisionNumber = 0;

			return mscorlibReference.version >= minimumDotNetVersion;
		}
		return false;
	}

	HRESULT HttpApplication::RegisterType(const ModuleID moduleId)
	{
		ATL::CComPtr<IMetaDataImport> metaDataImport;
		GUARD_FAILURE_HRESULT(GetMetaDataImport(moduleId, metaDataImport));

		ATL::CComPtr<IMetaDataEmit> metaDataEmit;
		GUARD_FAILURE_HRESULT(GetMetaDataEmit(moduleId, metaDataEmit));

		ULONG ulCodeRVA = 0;
		auto httpApplicationCtor = mdMethodDefNil;
		GUARD_FAILURE_HRESULT(GetRVAFromKnownDefaultCtor(metaDataImport,
			L"System.Web.HttpApplication",
			&m_httpApplicationTypeDef,
			&httpApplicationCtor,
			&ulCodeRVA));

		COR_SIGNATURE sigInitMethod[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT | IMAGE_CEE_CS_CALLCONV_HASTHIS,
			0x0,
			ELEMENT_TYPE_VOID
		};

		GUARD_FAILURE_HRESULT(metaDataImport->FindTypeDefByName(L"System.Web.HttpApplication", mdTokenNil, &m_typeDef));
		GUARD_FAILURE_HRESULT(metaDataImport->FindMethod(m_typeDef, L"Init", sigInitMethod, sizeof(sigInitMethod), &m_initMethodDef));

		mdModuleRef mscorlibRef;
		GUARD_FAILURE_HRESULT(DefineAssemblyMaxVersionRef(moduleId, L"mscorlib", &mscorlibRef));

		mdTypeRef eventArgsRef;
		GUARD_FAILURE_HRESULT(metaDataImport->FindTypeRef(mscorlibRef, L"System.EventArgs", &eventArgsRef));

		COR_SIGNATURE sigEventHandlerDelegate[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT | IMAGE_CEE_CS_CALLCONV_HASTHIS,
			0x2,
			ELEMENT_TYPE_VOID,
			ELEMENT_TYPE_OBJECT,
			ELEMENT_TYPE_CLASS,
			0x0,0x0 // compressed token
		};
		auto sigEventHandlerDelegateLength = CorSigCompressAndCompactToken(eventArgsRef, sigEventHandlerDelegate, 5, 6, sizeof(sigEventHandlerDelegate));

		GUARD_FAILURE_HRESULT(metaDataEmit->DefineMethod(m_typeDef,
			L"OnTraceContainerBeginRequest",
			mdPrivate | mdHideBySig | mdReuseSlot,
			sigEventHandlerDelegate,
			sigEventHandlerDelegateLength,
			ulCodeRVA,
			0,
			&m_onTraceContainerBeginRequestMethodDef));

		GUARD_FAILURE_HRESULT(metaDataEmit->DefineMethod(m_typeDef,
			L"OnTraceContainerEndRequest",
			mdPrivate | mdHideBySig | mdReuseSlot,
			sigEventHandlerDelegate,
			sigEventHandlerDelegateLength,
			ulCodeRVA,
			0,
			&m_onTraceContainerEndRequestMethodDef));

		GUARD_FAILURE_HRESULT(RegisterImplementationTypeDependencies(moduleId, metaDataEmit, metaDataImport));
			
		auto traceContainerString = L"TraceContainer.Current.ContextId";
		GUARD_FAILURE_HRESULT(metaDataEmit->DefineUserString(traceContainerString, static_cast<ULONG>(wcslen(traceContainerString)), &m_contextIdKey));

		return S_OK;
	}

	HRESULT HttpApplication::InjectTypeImplementation(ModuleID moduleId)
	{
		GUARD_FAILURE_HRESULT(InjectInitImplementation(moduleId));
		GUARD_FAILURE_HRESULT(InjectOnTraceContainerBeginRequestImplementation(moduleId));
		GUARD_FAILURE_HRESULT(InjectOnTraceContainerEndRequestImplementation(moduleId));

		return S_OK;
	}

	HRESULT HttpApplication::RegisterImplementationTypeDependencies(const ModuleID moduleId, ATL::CComPtr<IMetaDataEmit>& metaDataEmit, ATL::CComPtr<IMetaDataImport>& metaDataImport)
	{
		mdModuleRef mscorlibRef;
		GUARD_FAILURE_HRESULT(DefineAssemblyMaxVersionRef(moduleId, L"mscorlib", &mscorlibRef));

		mdTypeRef traceContainerBaseRef;
		GUARD_FAILURE_HRESULT(metaDataEmit->DefineTypeRefByName(mscorlibRef, L"TraceContainerBase", &traceContainerBaseRef));

		COR_SIGNATURE sigNotifyContextEnd[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT | IMAGE_CEE_CS_CALLCONV_HASTHIS,
			0x0,
			ELEMENT_TYPE_VOID
		};

		GUARD_FAILURE_HRESULT(metaDataEmit->DefineMemberRef(traceContainerBaseRef, L"NotifyContextEnd", sigNotifyContextEnd, sizeof(sigNotifyContextEnd), &m_traceContainerBaseNotifyContextEndRef));

		mdTypeRef traceContainerRef;
		GUARD_FAILURE_HRESULT(metaDataEmit->DefineTypeRefByName(mscorlibRef, L"TraceContainer", &traceContainerRef));

		COR_SIGNATURE sigGetCurrent[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT,
			0x0,
			ELEMENT_TYPE_CLASS,
			0x00, 0x00 // compressed token
		};
		auto sigGetCurrentLength = CorSigCompressAndCompactToken(traceContainerBaseRef, sigGetCurrent, 3, 4, sizeof(sigGetCurrent));

		GUARD_FAILURE_HRESULT(metaDataEmit->DefineMemberRef(traceContainerRef, L"GetCurrent", sigGetCurrent, sigGetCurrentLength, &m_traceContainerGetCurrentRef));

		mdTypeRef eventHandlerRef;
		GUARD_FAILURE_HRESULT(metaDataEmit->DefineTypeRefByName(mscorlibRef, L"System.EventHandler", &eventHandlerRef));

		COR_SIGNATURE sigEventHandlerConstructor[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT | IMAGE_CEE_CS_CALLCONV_HASTHIS,
			0x2,
			ELEMENT_TYPE_VOID,
			ELEMENT_TYPE_OBJECT,
			ELEMENT_TYPE_I // native int
		};
		GUARD_FAILURE_HRESULT(metaDataEmit->DefineMemberRef(eventHandlerRef, L".ctor", sigEventHandlerConstructor, sizeof(sigEventHandlerConstructor), &m_eventHandlerCtorRef));

		COR_SIGNATURE sigEventHandler[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT | IMAGE_CEE_CS_CALLCONV_HASTHIS,
			0x1,
			ELEMENT_TYPE_VOID,
			ELEMENT_TYPE_CLASS,
			0x0,0x0 // compressed token
		};
		auto sigEventHandlerLength = CorSigCompressAndCompactToken(eventHandlerRef, sigEventHandler, 4, 5, sizeof(sigEventHandler));

		GUARD_FAILURE_HRESULT(metaDataImport->FindMember(m_typeDef, L"add_EndRequest", sigEventHandler, sigEventHandlerLength, &m_addEndRequestDef));
		GUARD_FAILURE_HRESULT(metaDataImport->FindMember(m_typeDef, L"add_BeginRequest", sigEventHandler, sigEventHandlerLength, &m_addBeginRequestDef));

		mdTypeDef httpContextTypeDef;
		GUARD_FAILURE_HRESULT(metaDataImport->FindTypeDefByName(L"System.Web.HttpContext", mdTokenNil, &httpContextTypeDef));

		COR_SIGNATURE sigHttpApplicationGetContext[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT | IMAGE_CEE_CS_CALLCONV_HASTHIS,
			0x0,
			ELEMENT_TYPE_CLASS,
			0x0,0x0 // compressed token
		};
		auto sigHttpApplicationGetContextLength = CorSigCompressAndCompactToken(httpContextTypeDef, sigHttpApplicationGetContext, 3, 4, sizeof(sigHttpApplicationGetContext));

		GUARD_FAILURE_HRESULT(metaDataImport->FindMember(m_httpApplicationTypeDef, L"get_Context", sigHttpApplicationGetContext, sigHttpApplicationGetContextLength, &m_httpApplicationGetContext));

		mdTypeRef dictionaryTypeRef;
		GUARD_FAILURE_HRESULT(metaDataEmit->DefineTypeRefByName(mscorlibRef, L"System.Collections.IDictionary", &dictionaryTypeRef));

		COR_SIGNATURE sigHttpContextGetItems[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT | IMAGE_CEE_CS_CALLCONV_HASTHIS,
			0x0,
			ELEMENT_TYPE_CLASS,
			0x0,0x0 // compressed token
		};
		auto sigHttpContextGetItemsLength = CorSigCompressAndCompactToken(dictionaryTypeRef, sigHttpContextGetItems, 3, 4, sizeof(sigHttpContextGetItems));
		
		GUARD_FAILURE_HRESULT(metaDataImport->FindMember(httpContextTypeDef, L"get_Items", sigHttpContextGetItems, sigHttpContextGetItemsLength, &m_httpContextGetItems));

		GUARD_FAILURE_HRESULT(metaDataEmit->DefineTypeRefByName(mscorlibRef, L"System.Guid", &m_guidTypeRef));

		COR_SIGNATURE sigContextIdField[] =
		{
			IMAGE_CEE_CS_CALLCONV_FIELD,
			ELEMENT_TYPE_VALUETYPE,
			0x0,0x0 // compressed token
		};
		auto sigContextIdFieldLength = CorSigCompressAndCompactToken(m_guidTypeRef, sigContextIdField, 2, 3, sizeof(sigContextIdField));
		
		GUARD_FAILURE_HRESULT(metaDataEmit->DefineMemberRef(traceContainerBaseRef, L"_contextId", sigContextIdField, sigContextIdFieldLength, &m_traceContainerBaseContextIdRef));

		mdTypeRef systemObjectRef;
		GUARD_FAILURE_HRESULT(metaDataEmit->DefineTypeRefByName(mscorlibRef, L"System.Object", &systemObjectRef));

		COR_SIGNATURE sigObjectToString[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT | IMAGE_CEE_CS_CALLCONV_HASTHIS,
			0x0,
			ELEMENT_TYPE_STRING
		};
		
		GUARD_FAILURE_HRESULT(metaDataEmit->DefineMemberRef(systemObjectRef, L"ToString", sigObjectToString, sizeof(sigObjectToString), &m_objectToStringRef));

		COR_SIGNATURE sigDictionarySetItem[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT | IMAGE_CEE_CS_CALLCONV_HASTHIS,
			0x2,
			ELEMENT_TYPE_VOID,
			ELEMENT_TYPE_OBJECT,
			ELEMENT_TYPE_OBJECT
		};
		
		GUARD_FAILURE_HRESULT(metaDataEmit->DefineMemberRef(dictionaryTypeRef, L"set_Item", sigDictionarySetItem, sizeof(sigDictionarySetItem), &m_dictionarySetItem));
		
		COR_SIGNATURE sigHttpContextGetItem[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT | IMAGE_CEE_CS_CALLCONV_HASTHIS,
			0x1,
			ELEMENT_TYPE_OBJECT,
			ELEMENT_TYPE_OBJECT
		};

		GUARD_FAILURE_HRESULT(metaDataEmit->DefineMemberRef(dictionaryTypeRef, L"get_Item", sigHttpContextGetItem, sizeof(sigHttpContextGetItem), &m_dictionaryGetItem));
		
		COR_SIGNATURE sigGuidParse[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT,
			0x1,
			ELEMENT_TYPE_VALUETYPE,
			0x0,0x0, // compressed token
			ELEMENT_TYPE_STRING
		};
		auto sigGuidParseLength = CorSigCompressAndCompactToken(m_guidTypeRef, sigGuidParse, 3, 4, sizeof(sigGuidParse));

		GUARD_FAILURE_HRESULT(metaDataEmit->DefineMemberRef(m_guidTypeRef, L"Parse", sigGuidParse, sigGuidParseLength, &m_guidParseRef));

		COR_SIGNATURE sigSetContextId[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT | IMAGE_CEE_CS_CALLCONV_HASTHIS,
			0x1,
			ELEMENT_TYPE_VOID,
			ELEMENT_TYPE_VALUETYPE,
			0x0,0x0, // compressed token
		};
		auto sigSetContextIdLength = CorSigCompressAndCompactToken(m_guidTypeRef, sigSetContextId, 4, 5, sizeof(sigSetContextId));

		GUARD_FAILURE_HRESULT(metaDataEmit->DefineMemberRef(traceContainerBaseRef, L"SetContextId", sigSetContextId, sigSetContextIdLength, &m_traceContainerBaseSetContextIdRef));

		COR_SIGNATURE sigEndRequestLocalVariables[] =
		{
			IMAGE_CEE_CS_CALLCONV_LOCAL_SIG,
			0x1, // skipping CorSigCompressData (already one byte)
			ELEMENT_TYPE_CLASS,
			0x0,0x0 // TraceContainerBase
		};
		auto sigEndRequestLocalVariablesLength = CorSigCompressAndCompactToken(traceContainerBaseRef, sigEndRequestLocalVariables, 3, 4, sizeof(sigEndRequestLocalVariables));

		GUARD_FAILURE_HRESULT(metaDataEmit->GetTokenFromSig(sigEndRequestLocalVariables, sigEndRequestLocalVariablesLength, &m_endRequestLocalVariablesSignature));

		return S_OK;
	}

	HRESULT HttpApplication::InjectInitImplementation(ModuleID moduleId) const
	{
		InstructionList instructions;
		instructions.push_back(new Instruction(CEE_NOP));
		instructions.push_back(new Instruction(CEE_LDARG_0));
		instructions.push_back(new Instruction(CEE_LDARG_0));
		instructions.push_back(new Instruction(CEE_LDFTN, m_onTraceContainerBeginRequestMethodDef));
		instructions.push_back(new Instruction(CEE_NEWOBJ, m_eventHandlerCtorRef));
		instructions.push_back(new Instruction(CEE_CALL, m_addBeginRequestDef));
		instructions.push_back(new Instruction(CEE_NOP));

		instructions.push_back(new Instruction(CEE_LDARG_0));
		instructions.push_back(new Instruction(CEE_LDARG_0));
		instructions.push_back(new Instruction(CEE_LDFTN, m_onTraceContainerEndRequestMethodDef));
		instructions.push_back(new Instruction(CEE_NEWOBJ, m_eventHandlerCtorRef));
		instructions.push_back(new Instruction(CEE_CALL, m_addEndRequestDef));
		instructions.push_back(new Instruction(CEE_NOP));

		instructions.push_back(new Instruction(CEE_RET));

		GUARD_FAILURE_HRESULT(ReplaceMethodWith(moduleId, m_initMethodDef, instructions));

		return S_OK;
	}

	HRESULT HttpApplication::InjectOnTraceContainerBeginRequestImplementation(const ModuleID moduleId) const
	{
		InstructionList instructions;
		instructions.push_back(new Instruction(CEE_NOP));
		instructions.push_back(new Instruction(CEE_LDARG_0));
		instructions.push_back(new Instruction(CEE_CALL, m_httpApplicationGetContext));
		instructions.push_back(new Instruction(CEE_CALLVIRT, m_httpContextGetItems));
		instructions.push_back(new Instruction(CEE_LDSTR, m_contextIdKey));
		instructions.push_back(new Instruction(CEE_CALL, m_traceContainerGetCurrentRef));
		instructions.push_back(new Instruction(CEE_LDFLDA, m_traceContainerBaseContextIdRef));
		instructions.push_back(new Instruction(CEE_CONSTRAINED, m_guidTypeRef));
		instructions.push_back(new Instruction(CEE_CALLVIRT, m_objectToStringRef));
		instructions.push_back(new Instruction(CEE_CALLVIRT, m_dictionarySetItem));
		instructions.push_back(new Instruction(CEE_NOP));
		instructions.push_back(new Instruction(CEE_RET));

		GUARD_FAILURE_HRESULT(ReplaceMethodWith(moduleId, m_onTraceContainerBeginRequestMethodDef, instructions));

		return S_OK;
	}

	HRESULT HttpApplication::InjectOnTraceContainerEndRequestImplementation(const ModuleID moduleId) const
	{
		InstructionList instructions;
		instructions.push_back(new Instruction(CEE_NOP));
		instructions.push_back(new Instruction(CEE_CALL, m_traceContainerGetCurrentRef));
		instructions.push_back(new Instruction(CEE_STLOC_0));
		instructions.push_back(new Instruction(CEE_LDLOC_0));
		instructions.push_back(new Instruction(CEE_LDARG_0));
		instructions.push_back(new Instruction(CEE_CALL, m_httpApplicationGetContext));
		instructions.push_back(new Instruction(CEE_CALLVIRT, m_httpContextGetItems));
		instructions.push_back(new Instruction(CEE_LDSTR, m_contextIdKey));
		instructions.push_back(new Instruction(CEE_CALLVIRT, m_dictionaryGetItem));
		instructions.push_back(new Instruction(CEE_CALLVIRT, m_objectToStringRef));
		instructions.push_back(new Instruction(CEE_CALL, m_guidParseRef));
		instructions.push_back(new Instruction(CEE_CALLVIRT, m_traceContainerBaseSetContextIdRef));
		instructions.push_back(new Instruction(CEE_NOP));
		instructions.push_back(new Instruction(CEE_LDLOC_0));
		instructions.push_back(new Instruction(CEE_CALLVIRT, m_traceContainerBaseNotifyContextEndRef));
		instructions.push_back(new Instruction(CEE_NOP));
		instructions.push_back(new Instruction(CEE_RET));

		GUARD_FAILURE_HRESULT(ReplaceMethodWith(moduleId, m_onTraceContainerEndRequestMethodDef, instructions, m_endRequestLocalVariablesSignature));
		

		return S_OK;
	}
}
