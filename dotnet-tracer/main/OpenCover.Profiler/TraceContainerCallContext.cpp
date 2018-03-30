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

#include "ReleaseTrace.h"

#include "TraceContainerCallContext.h"
#include "TraceContainerBase.h"
#include "AssemblyRegistry.h"
#include "Instruction.h"

using namespace Instrumentation;
using namespace Injection;

namespace Context
{
	TraceContainerCallContext::TraceContainerCallContext(const ATL::CComPtr<ICorProfilerInfo>& profilerInfo,
		const std::shared_ptr<AssemblyRegistry>& assemblyRegistry,
		const std::shared_ptr<TraceContainerBase>& traceContainerBase) :
			InjectedType(profilerInfo, assemblyRegistry),
			m_traceContainerBase(traceContainerBase),
			m_typeDef(mdTypeDefNil),
			m_ctorDef(mdMethodDefNil),
			m_cctorDef(mdMethodDefNil),
			m_traceContainerKeyFieldDef(mdFieldDefNil),
			m_getCurrentMethodDef(mdMethodDefNil),
			m_traceContainerString(mdStringNil),
			m_getCurrentLocalVariablesSignature(mdSignatureNil),
			m_callContextLogicalGetDataMethodDef(mdMethodDefNil),
			m_callContextLogicalSetDataMethodDef(mdMethodDefNil)
	{
	}

	TraceContainerCallContext::~TraceContainerCallContext()
	{
	}

	bool TraceContainerCallContext::ShouldRegisterType(const ModuleID moduleId) const
	{
		if (!HasTypeDef(moduleId, L"System.Object"))
		{
			return false;
		}

		AssemblyVersion mscorlibVersion;
		if (m_assemblyRegistry->FindMaxAssemblyVersion(L"mscorlib", mscorlibVersion))
		{
			// LogicalGetData(String) and LogicalSetData(String, Object) available since .NET 2.0
			return mscorlibVersion.majorVersion >= 2;
		}
		return false;
	}

	HRESULT TraceContainerCallContext::RegisterType(const ModuleID moduleId)
	{
		GUARD_FAILURE_HRESULT(m_traceContainerBase->RegisterTypeInModule(moduleId));

		ATL::CComPtr<IMetaDataImport> metaDataImport;
		GUARD_FAILURE_HRESULT(GetMetaDataImport(moduleId, metaDataImport));

		ATL::CComPtr<IMetaDataEmit> metaDataEmit;
		GUARD_FAILURE_HRESULT(GetMetaDataEmit(moduleId, metaDataEmit));
		
		ULONG ulCodeRVA = 0;
		auto systemObject = mdTypeDefNil;
		auto systemObjectCtor = mdMethodDefNil;
		GUARD_FAILURE_HRESULT(GetRVAFromKnownDefaultCtor(metaDataImport,
			L"System.Object",
			&systemObject,
			&systemObjectCtor,
			&ulCodeRVA));

		auto traceContainerString = L"traceContainer";
		GUARD_FAILURE_HRESULT(metaDataEmit->DefineUserString(traceContainerString, (ULONG)wcslen(traceContainerString), &m_traceContainerString));

		GUARD_FAILURE_HRESULT(metaDataEmit->DefineTypeDef(L"TraceContainer",
			tdClass | tdPublic | tdAutoLayout | tdAnsiClass | tdBeforeFieldInit,
			m_traceContainerBase->GetType(),
			nullptr,
			&m_typeDef));

		COR_SIGNATURE sigTraceContainerKeyField[] =
		{
			IMAGE_CEE_CS_CALLCONV_FIELD,
			ELEMENT_TYPE_STRING
		};

		GUARD_FAILURE_HRESULT(metaDataEmit->DefineField(m_typeDef, L"TraceContainerName",
			mdPrivate | mdStatic | fdInitOnly,
			sigTraceContainerKeyField,
			sizeof(sigTraceContainerKeyField),
			0,
			nullptr,
			0,
			&m_traceContainerKeyFieldDef));

		COR_SIGNATURE sigStaticCtorMethod[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT,
			0x0,
			ELEMENT_TYPE_VOID
		};

		GUARD_FAILURE_HRESULT(metaDataEmit->DefineMethod(m_typeDef,
			L".cctor",
			mdPrivate | mdHideBySig | mdSpecialName | mdRTSpecialName | mdStatic,
			sigStaticCtorMethod,
			sizeof(sigStaticCtorMethod),
			ulCodeRVA,
			0,
			&m_cctorDef));

		COR_SIGNATURE sigCtorMethod[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT | IMAGE_CEE_CS_CALLCONV_HASTHIS,
			0x0,
			ELEMENT_TYPE_VOID
		};

		GUARD_FAILURE_HRESULT(metaDataEmit->DefineMethod(m_typeDef,
			L".ctor",
			mdPublic | mdHideBySig | mdSpecialName | mdRTSpecialName,
			sigCtorMethod,
			sizeof(sigCtorMethod),
			ulCodeRVA,
			0,
			&m_ctorDef));

		COR_SIGNATURE sigGetCurrentMethod[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT,
			0x0,
			ELEMENT_TYPE_CLASS,
			0x0,0x0 // compressed token
		};

		auto sigGetCurrentMethodLength = CorSigCompressAndCompactToken(m_traceContainerBase->GetType(), sigGetCurrentMethod, 3, 4, sizeof(sigGetCurrentMethod));
		GUARD_FAILURE_HRESULT(metaDataEmit->DefineMethod(m_typeDef,
			L"GetCurrent",
			mdPublic | mdHideBySig | mdSpecialName | mdStatic,
			sigGetCurrentMethod,
			sigGetCurrentMethodLength,
			ulCodeRVA,
			0,
			&m_getCurrentMethodDef));

		// Note: Must have SecuritySafeCriticalAttribute to call CallContext::LogicalGetData 
		mdTypeDef attributeTypeDef;
		GUARD_FAILURE_HRESULT(metaDataImport->FindTypeDefByName(L"System.Security.SecuritySafeCriticalAttribute",
			NULL, &attributeTypeDef));

		mdMethodDef attributeCtor;
		GUARD_FAILURE_HRESULT(metaDataImport->FindMember(attributeTypeDef,
			L".ctor", sigCtorMethod, sizeof(sigCtorMethod), &attributeCtor));

		mdCustomAttribute customAttr;
		GUARD_FAILURE_HRESULT(metaDataEmit->DefineCustomAttribute(m_getCurrentMethodDef, attributeCtor, NULL, 0, &customAttr));

		COR_SIGNATURE sigGetCurrentLocalVariables[] =
		{
			IMAGE_CEE_CS_CALLCONV_LOCAL_SIG,
			0x3, // skipping CorSigCompressData (already one byte)
			ELEMENT_TYPE_CLASS,
			0x0,0x0,				// TraceContainer
			ELEMENT_TYPE_BOOLEAN,
			ELEMENT_TYPE_CLASS,
			0x0,0x0					// TraceContainer
		};

		auto sigGetCurrentLocalVariablesLength = CorSigCompressAndCompactToken(m_typeDef, sigGetCurrentLocalVariables, 7, 8, sizeof(sigGetCurrentLocalVariables));
		sigGetCurrentLocalVariablesLength = CorSigCompressAndCompactToken(m_typeDef, sigGetCurrentLocalVariables, 3, 4, sigGetCurrentLocalVariablesLength);
		GUARD_FAILURE_HRESULT(metaDataEmit->GetTokenFromSig(sigGetCurrentLocalVariables, sigGetCurrentLocalVariablesLength, &m_getCurrentLocalVariablesSignature));
	
		GUARD_FAILURE_HRESULT(RegisterImplementationTypeDependencies(moduleId, metaDataImport));

		return S_OK;
	}

	HRESULT TraceContainerCallContext::InjectTypeImplementation(const ModuleID moduleId)
	{
		GUARD_FAILURE_HRESULT(m_traceContainerBase->InjectTypeImplementationInModule(moduleId));
		GUARD_FAILURE_HRESULT(InjectStaticCtorImplementation(moduleId));
		GUARD_FAILURE_HRESULT(InjectCtorImplementation(moduleId));
		GUARD_FAILURE_HRESULT(InjectGetCurrentImplementation(moduleId));

		return S_OK;
	}

	mdFieldDef TraceContainerCallContext::GetContextIdHighField() const
	{
		return m_traceContainerBase->GetContextIdHighField();
	}

	mdFieldDef TraceContainerCallContext::GetContextIdLowField() const
	{
		return m_traceContainerBase->GetContextIdLowField();
	}

	HRESULT TraceContainerCallContext::RegisterImplementationTypeDependencies(const ModuleID moduleId, ATL::CComPtr<IMetaDataImport>& metaDataImport)
	{
		mdTypeDef callContextToken;
		GUARD_FAILURE_HRESULT(metaDataImport->FindTypeDefByName(L"System.Runtime.Remoting.Messaging.CallContext", mdTokenNil, &callContextToken));

		COR_SIGNATURE sigLogicalGetData[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT,
			0x1,
			ELEMENT_TYPE_OBJECT,
			ELEMENT_TYPE_STRING
		};
		GUARD_FAILURE_HRESULT(metaDataImport->FindMethod(callContextToken, L"LogicalGetData", sigLogicalGetData, sizeof(sigLogicalGetData), &m_callContextLogicalGetDataMethodDef));

		COR_SIGNATURE sigLogicalSetData[] =
		{
			IMAGE_CEE_CS_CALLCONV_DEFAULT,
			0x2,
			ELEMENT_TYPE_VOID,
			ELEMENT_TYPE_STRING,
			ELEMENT_TYPE_OBJECT
		};
		GUARD_FAILURE_HRESULT(metaDataImport->FindMethod(callContextToken, L"LogicalSetData", sigLogicalSetData, sizeof(sigLogicalSetData), &m_callContextLogicalSetDataMethodDef));

		return S_OK;
	}

	HRESULT TraceContainerCallContext::InjectStaticCtorImplementation(const ModuleID moduleId) const
	{
		InstructionList staticConstructorInstructions;
		staticConstructorInstructions.push_back(new Instruction(CEE_NOP));
		staticConstructorInstructions.push_back(new Instruction(CEE_LDSTR, m_traceContainerString));
		staticConstructorInstructions.push_back(new Instruction(CEE_STSFLD, m_traceContainerKeyFieldDef));
		staticConstructorInstructions.push_back(new Instruction(CEE_RET));
		GUARD_FAILURE_HRESULT(ReplaceMethodWith(moduleId, m_cctorDef, staticConstructorInstructions));

		return S_OK;
	}

	HRESULT TraceContainerCallContext::InjectCtorImplementation(const ModuleID moduleId) const
	{
		InstructionList constructorInstructions;
		constructorInstructions.push_back(new Instruction(CEE_LDARG_0));
		constructorInstructions.push_back(new Instruction(CEE_CALL, m_traceContainerBase->GetCtorMethod()));
		constructorInstructions.push_back(new Instruction(CEE_NOP));
		constructorInstructions.push_back(new Instruction(CEE_RET));
		GUARD_FAILURE_HRESULT(ReplaceMethodWith(moduleId, m_ctorDef, constructorInstructions));

		return S_OK;
	}

	HRESULT TraceContainerCallContext::InjectGetCurrentImplementation(const ModuleID moduleId) const
	{
		InstructionList getCurrentInstructions;
		getCurrentInstructions.push_back(new Instruction(CEE_NOP));
		getCurrentInstructions.push_back(new Instruction(CEE_LDSTR, m_traceContainerString));
		getCurrentInstructions.push_back(new Instruction(CEE_CALL, m_callContextLogicalGetDataMethodDef));
		getCurrentInstructions.push_back(new Instruction(CEE_ISINST, m_typeDef));
		getCurrentInstructions.push_back(new Instruction(CEE_STLOC_0));
		getCurrentInstructions.push_back(new Instruction(CEE_LDLOC_0));
		getCurrentInstructions.push_back(new Instruction(CEE_LDNULL));
		getCurrentInstructions.push_back(new Instruction(CEE_CGT_UN));
		getCurrentInstructions.push_back(new Instruction(CEE_STLOC_1));
		getCurrentInstructions.push_back(new Instruction(CEE_LDLOC_1));
		getCurrentInstructions.push_back(new Instruction(CEE_BRFALSE_S, 0x1E));
		getCurrentInstructions.push_back(new Instruction(CEE_NOP));
		getCurrentInstructions.push_back(new Instruction(CEE_LDLOC_0));
		getCurrentInstructions.push_back(new Instruction(CEE_STLOC_2));
		getCurrentInstructions.push_back(new Instruction(CEE_BR_S, 0x34));
		getCurrentInstructions.push_back(new Instruction(CEE_NEWOBJ, m_ctorDef));
		getCurrentInstructions.push_back(new Instruction(CEE_STLOC_0));
		getCurrentInstructions.push_back(new Instruction(CEE_LDSTR, m_traceContainerString));
		getCurrentInstructions.push_back(new Instruction(CEE_LDLOC_0));
		getCurrentInstructions.push_back(new Instruction(CEE_CALL, m_callContextLogicalSetDataMethodDef));
		getCurrentInstructions.push_back(new Instruction(CEE_NOP));
		getCurrentInstructions.push_back(new Instruction(CEE_LDLOC_0));
		getCurrentInstructions.push_back(new Instruction(CEE_STLOC_2));
		getCurrentInstructions.push_back(new Instruction(CEE_BR_S, 0x34));
		getCurrentInstructions.push_back(new Instruction(CEE_LDLOC_2));
		getCurrentInstructions.push_back(new Instruction(CEE_RET));

		// set offsets of branch targets
		getCurrentInstructions[15]->m_offset = 0x1E;
		getCurrentInstructions[24]->m_offset = 0x34;

		getCurrentInstructions[10]->m_isBranch = true;
		getCurrentInstructions[10]->m_branchOffsets.push_back(0);
		getCurrentInstructions[10]->m_branches.push_back(getCurrentInstructions[15]);

		getCurrentInstructions[14]->m_isBranch = true;
		getCurrentInstructions[14]->m_branchOffsets.push_back(0);
		getCurrentInstructions[14]->m_branches.push_back(getCurrentInstructions[24]);

		getCurrentInstructions[23]->m_isBranch = true;
		getCurrentInstructions[23]->m_branchOffsets.push_back(0);
		getCurrentInstructions[23]->m_branches.push_back(getCurrentInstructions[24]);

		GUARD_FAILURE_HRESULT(ReplaceMethodWith(moduleId, m_getCurrentMethodDef, getCurrentInstructions, m_getCurrentLocalVariablesSignature));

		return S_OK;
	}
}