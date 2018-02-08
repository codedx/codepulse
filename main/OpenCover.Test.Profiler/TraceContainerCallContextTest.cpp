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
#include "TraceContainerCallContext.h"
#include "InjectedTypeTestFixture.h"
#include "MockIMetaDataImport.h"
#include "MockICorProfilerInfo.h"
#include "MockIMetaDataAssemblyImport.h"

using namespace Injection;
using namespace Context;
using namespace testing;
using namespace std;

class TraceContainerCallContextTest : public InjectedTypeTestFixture 
{
protected:
	TraceContainerCallContextTest() :
		InjectedTypeTestFixture(),
		traceContainerBase(make_shared<TraceContainerBase>(profilerInfoPtr, assemblyRegistry, 0x12345678))
	{
	}

	void SetUp() override
	{
		InjectedTypeTestFixture::SetUp();
	}

	void SetUpAssemblyRegistry(const USHORT majorVersion, const USHORT minorVersion)
	{
		const auto publicKeyTokenSize = 8;
		BYTE publicKeyToken[] = { 0, 1, 2, 3, 4, 5, 6, 7 };

		const auto assemblyNameSize = 10;
		const LPWSTR assemblyName = L"mscorlib";

		ASSEMBLYMETADATA metadata1;
		metadata1.usMajorVersion = majorVersion;
		metadata1.usMinorVersion = minorVersion;
		metadata1.usBuildNumber = 0;
		metadata1.usRevisionNumber = 0;

		EXPECT_CALL(metaDataAssemblyImport, GetAssemblyProps(_, _, _, _, _, _, _, _, _))
			.WillOnce(DoAll(
				SetArgPointee<1>(&publicKeyToken),
				SetArgPointee<2>(publicKeyTokenSize),
				SetArrayArgument<4>(assemblyName, assemblyName + 8),
				SetArgPointee<6>(assemblyNameSize),
				SetArgPointee<7>(metadata1),
				Return(S_OK)));

		EXPECT_CALL(metaDataAssemblyImport, EnumAssemblyRefs(_, _, _, _))
			.WillRepeatedly(Return(S_FALSE));
	}

	shared_ptr<TraceContainerBase> traceContainerBase;

	const int shortCircuitRetVal = -0x123;

	MockIMetaDataImport metaDataImport;
};

TEST_F(TraceContainerCallContextTest, RegisterTypeSkippedForUnrelatedModule)
{
	TraceContainerCallContext traceContainerCallContext(profilerInfoPtr, assemblyRegistry, traceContainerBase);

	EXPECT_CALL(profilerInfo, GetModuleMetaData(_, _, IID_IMetaDataImport, _))
		.WillOnce(DoAll(SetArgPointee<3>(&metaDataImport), Return(S_OK)));

	EXPECT_CALL(metaDataImport, FindTypeDefByName(L"System.Object", _, _))
		.WillOnce(Return(E_FAIL));

	ASSERT_EQ(S_FALSE, traceContainerCallContext.RegisterTypeInModule(1));
}

TEST_F(TraceContainerCallContextTest, RegisterTypeOccursForRelatedModule)
{
	TraceContainerCallContext traceContainerCallContext(profilerInfoPtr, assemblyRegistry, traceContainerBase);

	EXPECT_CALL(profilerInfo, GetModuleMetaData(_, _, IID_IMetaDataImport, _))
		.WillOnce(DoAll(SetArgPointee<3>(&metaDataImport), Return(S_OK)))
		.WillOnce(DoAll(SetArgPointee<3>(&metaDataImport), Return(S_OK)))
		.WillOnce(Return(shortCircuitRetVal));

	EXPECT_CALL(metaDataImport, FindTypeDefByName(L"System.Object", _, _))
		.WillOnce(Return(S_OK))
		.WillOnce(Return(S_OK));

	SetUpAssemblyRegistry(4, 0);

	assemblyRegistry->RecordAssemblyMetadataForModule(1);

	ASSERT_EQ(shortCircuitRetVal, traceContainerCallContext.RegisterTypeInModule(1));
}

TEST_F(TraceContainerCallContextTest, RegisterTypeSkippedForDotNet11)
{
	TraceContainerCallContext traceContainerCallContext(profilerInfoPtr, assemblyRegistry, traceContainerBase);

	EXPECT_CALL(profilerInfo, GetModuleMetaData(_, _, IID_IMetaDataImport, _))
		.WillOnce(DoAll(SetArgPointee<3>(&metaDataImport), Return(S_OK)));

	EXPECT_CALL(metaDataImport, FindTypeDefByName(L"System.Object", _, _))
		.WillOnce(Return(S_OK));

	SetUpAssemblyRegistry(1, 1);

	assemblyRegistry->RecordAssemblyMetadataForModule(1);

	ASSERT_EQ(S_FALSE, traceContainerCallContext.RegisterTypeInModule(1));
}

TEST_F(TraceContainerCallContextTest, InjectTypeImplementationFailsIfRegisterTypeFails)
{
	TraceContainerCallContext traceContainerCallContext(profilerInfoPtr, assemblyRegistry, traceContainerBase);

	EXPECT_CALL(profilerInfo, GetModuleMetaData(_, _, IID_IMetaDataImport, _))
		.WillOnce(DoAll(SetArgPointee<3>(&metaDataImport), Return(S_OK)))
		.WillOnce(DoAll(SetArgPointee<3>(&metaDataImport), Return(S_OK)))
		.WillOnce(Return(shortCircuitRetVal));

	EXPECT_CALL(metaDataImport, FindTypeDefByName(L"System.Object", _, _))
		.WillOnce(Return(S_OK))
		.WillOnce(Return(S_OK));

	SetUpAssemblyRegistry(4, 0);

	assemblyRegistry->RecordAssemblyMetadataForModule(1);

	ASSERT_EQ(shortCircuitRetVal, traceContainerCallContext.RegisterTypeInModule(1));
	ASSERT_EQ(E_ILLEGAL_METHOD_CALL, traceContainerCallContext.InjectTypeImplementationInModule(1));
}

TEST_F(TraceContainerCallContextTest, InjectTypeImplementationFailsIfRegisterTypeNotCalled)
{
	TraceContainerCallContext traceContainerCallContext(profilerInfoPtr, assemblyRegistry, traceContainerBase);
	ASSERT_EQ(E_ILLEGAL_METHOD_CALL, traceContainerCallContext.InjectTypeImplementationInModule(1));
}