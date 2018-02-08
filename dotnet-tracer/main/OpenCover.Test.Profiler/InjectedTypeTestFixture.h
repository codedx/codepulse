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

#include "stdafx.h"

#include "InjectedType.h"
#include "AssemblyRegistry.h"
#include "MockICorProfilerInfo.h"
#include "MockIMetaDataAssemblyImport.h"
#include "MockIMetaDataAssemblyEmit.h"

class InjectedTypeTestFixture : public testing::Test 
{
protected:
	InjectedTypeTestFixture() :
		Test(),
		profilerInfoPtr(&profilerInfo),
		assemblyRegistry(std::make_shared<Injection::AssemblyRegistry>(profilerInfoPtr))
	{
	}

	void SetUp() override
	{
		EXPECT_CALL(profilerInfo, GetModuleMetaData(testing::_, testing::_, IID_IMetaDataAssemblyImport, testing::_))
			.WillRepeatedly(DoAll(testing::SetArgPointee<3>(&metaDataAssemblyImport), testing::Return(S_OK)));
		EXPECT_CALL(profilerInfo, GetModuleMetaData(testing::_, testing::_, IID_IMetaDataAssemblyEmit, testing::_))
			.WillRepeatedly(DoAll(testing::SetArgPointee<3>(&metaDataAssemblyEmit), testing::Return(S_OK)));
	}

	void TearDown() override
	{
	}

	MockICorProfilerInfo profilerInfo;
	CComPtr<ICorProfilerInfo> profilerInfoPtr;

	std::shared_ptr<Injection::AssemblyRegistry> assemblyRegistry;

	MockIMetaDataAssemblyImport metaDataAssemblyImport;
	MockIMetaDataAssemblyEmit metaDataAssemblyEmit;
};
