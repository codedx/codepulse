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

#include "InjectedType.h"
#include "TraceContainerBase.h"
#include "InjectedTypeTestFixture.h"
#include "MockICorProfilerInfo.h"
#include "MockIMetaDataImport.h"

using namespace Injection;
using namespace Context;
using namespace testing;
using namespace std;

class TraceContainerBaseTest : public InjectedTypeTestFixture 
{
protected:
	void SetUp() override
	{
		InjectedTypeTestFixture::SetUp();
	}

	void SetUpGetModuleMetaData(const bool isSuccessCase)
	{
		if (isSuccessCase)
		{
			EXPECT_CALL(profilerInfo, GetModuleMetaData(_, _, IID_IMetaDataImport, _))
				.WillOnce(DoAll(SetArgPointee<3>(&metaDataImport), Return(S_OK)));
		}
		else
		{
			EXPECT_CALL(profilerInfo, GetModuleMetaData(_, _, IID_IMetaDataImport, _))
				.WillOnce(DoAll(SetArgPointee<3>(&metaDataImport), Return(S_OK)))
				.WillOnce(Return(shortCircuitRetVal));
		}
	}

	void SetUpFindTypeDefByName(const bool isSuccessCase)
	{
		if (isSuccessCase)
		{
			EXPECT_CALL(metaDataImport, FindTypeDefByName(L"System.Object", _, _))
				.WillOnce(Return(S_OK));
		}
		else
		{
			EXPECT_CALL(metaDataImport, FindTypeDefByName(L"System.Object", _, _))
				.WillOnce(Return(E_FAIL));
		}
	}

	const int shortCircuitRetVal = -0x123;

	MockIMetaDataImport metaDataImport;
};

TEST_F(TraceContainerBaseTest, RegisterTypeSkippedForUnrelatedModule)
{
	TraceContainerBase traceContainerBase(profilerInfoPtr, assemblyRegistry, 0x12345678);

	SetUpGetModuleMetaData(true);
	SetUpFindTypeDefByName(false);
	
	ASSERT_EQ(S_FALSE, traceContainerBase.RegisterTypeInModule(1));
}

TEST_F(TraceContainerBaseTest, RegisterTypeOccursForRelatedModule)
{
	TraceContainerBase traceContainerBase(profilerInfoPtr, assemblyRegistry, 0x12345678);

	SetUpGetModuleMetaData(false);
	SetUpFindTypeDefByName(true);

	ASSERT_EQ(shortCircuitRetVal, traceContainerBase.RegisterTypeInModule(1));
}

TEST_F(TraceContainerBaseTest, InjectTypeImplementationFailsIfRegisterTypeFails)
{
	TraceContainerBase traceContainerBase(profilerInfoPtr, assemblyRegistry, 0x12345678);

	SetUpGetModuleMetaData(false);
	SetUpFindTypeDefByName(true);

	ASSERT_EQ(shortCircuitRetVal, traceContainerBase.RegisterTypeInModule(1));
	ASSERT_EQ(E_ILLEGAL_METHOD_CALL, traceContainerBase.InjectTypeImplementationInModule(1));
}

TEST_F(TraceContainerBaseTest, InjectTypeImplementationFailsIfRegisterTypeNotCalled)
{
	TraceContainerBase traceContainerBase(profilerInfoPtr, assemblyRegistry, 0x12345678);
	ASSERT_EQ(E_ILLEGAL_METHOD_CALL, traceContainerBase.InjectTypeImplementationInModule(1));
}