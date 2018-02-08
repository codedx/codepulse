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

#include <memory>
#include <map>

#include "ExceptionHandler.h"

#define WIDEN2(x) L ## x
#define WIDEN(x) WIDEN2(x)
#define __WFILE__ WIDEN(__FILE__)

#define GUARD_FAILURE_HRESULT(hr) { \
		const auto result = hr; \
		if (!SUCCEEDED(result)) \
		{ \
			RELTRACE(L"Call *failed* with HRESULT 0x%X (%s:%d)", result, __WFILE__, __LINE__); \
			return (result); \
		} \
	 }
	
namespace Injection
{
	class AssemblyRegistry;

	class InjectedType
	{
	public:
		InjectedType(const ATL::CComPtr<ICorProfilerInfo>& profilerInfo,
			const std::shared_ptr<AssemblyRegistry>& assemblyRegistry);

		virtual ~InjectedType()
		{
		}

		bool IsRegistered() const;

		HRESULT RegisterTypeInModule(const ModuleID moduleId);

		HRESULT InjectTypeImplementationInModule(const ModuleID moduleId);

	protected:
		virtual bool ShouldRegisterType(const ModuleID moduleId) const = 0;
		virtual HRESULT RegisterType(const ModuleID moduleId) = 0;

		virtual HRESULT InjectTypeImplementation(const ModuleID moduleId) = 0;

		HRESULT GetMetaDataImport(const ModuleID moduleId, ATL::CComPtr<IMetaDataImport>& metaDataImport) const;
		HRESULT GetMetaDataEmit(const ModuleID moduleId, ATL::CComPtr<IMetaDataEmit>& metaDataEmit) const;
		HRESULT GetMetaDataAssemblyEmit(const ModuleID moduleId, ATL::CComPtr<IMetaDataAssemblyEmit>& metaDataAssemblyEmit) const;

		HRESULT DefineAssemblyMaxVersionRef(const ModuleID moduleId, LPCWSTR assemblyName, mdModuleRef* moduleRef) const;
		bool HasTypeDef(const ModuleID moduleId, const LPCWSTR typeDefName) const;

		HRESULT ReplaceMethodWith(const ModuleID moduleId, const mdToken functionToken, Instrumentation::InstructionList &instructions, const mdSignature localVarSigTok = mdSignatureNil, const unsigned minimumStackSize = 8) const;
		HRESULT ReplaceMethodWith(ModuleID moduleId, mdToken functionToken, Instrumentation::InstructionList &instructions, const mdSignature localVarSigTok, const unsigned minimumStackSize, Instrumentation::ExceptionHandlerList &exceptions) const;

		static ULONG CorSigCompressAndCompactToken(mdToken tk, PCOR_SIGNATURE sig, int indexStart, int indexEnd, int length);

		static HRESULT GetRVAFromKnownDefaultCtor(ATL::CComPtr<IMetaDataImport>& metaDataImport,
			const LPCWSTR knownTypeDefName,
			mdTypeDef* pKnownTypeDef,
			mdMethodDef* pKnownTypeDefaultCtorDef,
			ULONG* pCodeRVA);

		ATL::CComPtr<ICorProfilerInfo> m_profilerInfo;
		std::shared_ptr<AssemblyRegistry> m_assemblyRegistry;

		std::map<ModuleID, bool> m_registrationMap;
	};
}

