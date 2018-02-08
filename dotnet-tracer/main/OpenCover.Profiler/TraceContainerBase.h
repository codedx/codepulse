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
#include "InjectedType.h"

namespace Context
{
	class TraceContainerBase :
		public Injection::InjectedType
	{
	public:
		TraceContainerBase(const ATL::CComPtr<ICorProfilerInfo>& profilerInfo,
			const std::shared_ptr<Injection::AssemblyRegistry>& assemblyRegistry,
			const mdMethodDef cuckooSafeToken);

		virtual ~TraceContainerBase();

		mdTypeDef GetType() const { return m_typeDef; }

		mdMethodDef GetCtorMethod() const { return m_ctorMethodDef; }

		mdFieldDef GetContextIdHighField() const { return m_contextIdHighFieldDef; }
		mdFieldDef GetContextIdLowField() const { return m_contextIdLowFieldDef; }

	private:
		bool ShouldRegisterType(const ModuleID moduleId) const override;
		HRESULT RegisterType(const ModuleID moduleId) override;

		HRESULT InjectTypeImplementation(ModuleID moduleId) override;

		HRESULT RegisterImplementationTypeDependencies(const ModuleID moduleId, ATL::CComPtr<IMetaDataImport>& metaDataImport);

		HRESULT InjectCtorImplementation(const ModuleID moduleId) const;
		HRESULT InjectNotifyContextEndImplementation(const ModuleID moduleId) const;
		HRESULT InjectSetContextIdImplementation(const ModuleID moduleId) const;

		mdMethodDef m_cuckooSafeToken;

		mdTypeDef m_typeDef;
		mdMethodDef m_ctorMethodDef;
		mdMethodDef m_notifyContextEndMethodDef;
		mdMethodDef m_setContextIdMethodDef;

		mdFieldDef m_contextIdHighFieldDef;
		mdFieldDef m_contextIdLowFieldDef;
		mdFieldDef m_contextIdFieldDef;


		mdTypeDef m_systemObject;
		mdMethodDef m_systemObjectCtor;

		mdTypeDef m_guidTypeDef;
		mdMethodDef m_guidNewGuidMethodDef;
		mdMethodDef m_guidToByteArrayMethodDef;

		mdMethodDef m_bitConverterToUInt64MethodDef;

		mdSignature m_setContextIdLocalVariablesSignature;
	};
}
