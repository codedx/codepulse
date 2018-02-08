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

#include "AssemblyRegistry.h"
#include "InjectedType.h"
#include "InjectedTypeTestFixture.h"
#include "MockIMethodMalloc.h"

using namespace Injection;
using namespace Instrumentation;
using namespace testing;
using namespace std;

class InjectedTypeTest : public InjectedTypeTestFixture 
{
protected:
	InjectedTypeTest() :
		InjectedTypeTestFixture()
	{
	}

	void SetUp() override
	{
		InjectedTypeTestFixture::SetUp();
	}

	void TearDown() override
	{
	}
};

class MockInjectedType : public InjectedType
{
public:
	MockInjectedType(const CComPtr<ICorProfilerInfo>& profilerInfo,
		const shared_ptr<AssemblyRegistry>& assemblyRegistry) :
		InjectedType(profilerInfo, assemblyRegistry)
	{
	}

protected:
	MOCK_CONST_METHOD1(ShouldRegisterType, bool(const ModuleID moduleId));
	MOCK_METHOD1(RegisterType, HRESULT(const ModuleID moduleId));
	MOCK_METHOD1(InjectTypeImplementation, HRESULT(const ModuleID moduleId));

	FRIEND_TEST(InjectedTypeTest, DoesNotCallRegisterTypeIfTypeShouldNotBeRegisteredInModule);
	FRIEND_TEST(InjectedTypeTest, CallsRegisterTypeIfTypeShouldBeRegisteredInModule);
	FRIEND_TEST(InjectedTypeTest, FailsToInjectImplementationIfRegisterTypeNotCalledForModule);
	FRIEND_TEST(InjectedTypeTest, FailsToInjectImplementationIfRegisterTypeCalledForOtherModule);
	FRIEND_TEST(InjectedTypeTest, CanReplaceBasicMethod);
	FRIEND_TEST(InjectedTypeTest, CanReplaceMethodWithLocalVariablesAndCustomStackSize);
	FRIEND_TEST(InjectedTypeTest, CanReplaceMethodWithExceptionHandler);
	FRIEND_TEST(InjectedTypeTest, CanDefineAssemblyMaxVersionRef);
};

TEST_F(InjectedTypeTest, DoesNotCallRegisterTypeIfTypeShouldNotBeRegisteredInModule)
{
	MockInjectedType injectedType(profilerInfoPtr, assemblyRegistry);

	EXPECT_CALL(injectedType, ShouldRegisterType(_))
		.WillOnce(Return(false));

	EXPECT_CALL(injectedType, RegisterType(_))
		.Times(0);

	injectedType.RegisterTypeInModule(1);
}

TEST_F(InjectedTypeTest, CallsRegisterTypeIfTypeShouldBeRegisteredInModule)
{
	MockInjectedType injectedType(profilerInfoPtr, assemblyRegistry);

	EXPECT_CALL(injectedType, ShouldRegisterType(_))
		.WillOnce(Return(true));

	EXPECT_CALL(injectedType, RegisterType(_))
		.Times(1);

	injectedType.RegisterTypeInModule(1);
}

TEST_F(InjectedTypeTest, FailsToInjectImplementationIfRegisterTypeNotCalledForModule)
{
	MockInjectedType injectedType(profilerInfoPtr, assemblyRegistry);

	ASSERT_EQ(E_ILLEGAL_METHOD_CALL, injectedType.InjectTypeImplementationInModule(1));
}

TEST_F(InjectedTypeTest, FailsToInjectImplementationIfRegisterTypeCalledForOtherModule)
{
	MockInjectedType injectedType(profilerInfoPtr, assemblyRegistry);

	EXPECT_CALL(injectedType, ShouldRegisterType(_))
		.WillOnce(Return(true));

	EXPECT_CALL(injectedType, RegisterType(_))
		.WillOnce(Return(S_OK));

	EXPECT_CALL(injectedType, InjectTypeImplementation(_))
		.WillOnce(Return(S_OK));

	injectedType.RegisterTypeInModule(1);

	ASSERT_EQ(E_ILLEGAL_METHOD_CALL, injectedType.InjectTypeImplementationInModule(2));
	ASSERT_EQ(S_OK, injectedType.InjectTypeImplementationInModule(1));
}

TEST_F(InjectedTypeTest, CanReplaceBasicMethod)
{
	MockInjectedType injectedType(profilerInfoPtr, assemblyRegistry);

	const BYTE methodBytes[] = { 0x09, CEE_NOP, CEE_RET };
	LPCBYTE methodBytesStart = &methodBytes[0];
	const auto methodBytesSize = 0x3;

	EXPECT_CALL(profilerInfo, GetILFunctionBody(_, _, _, _))
		.WillOnce(DoAll(
			SetArrayArgument<2>(&methodBytesStart, &methodBytesStart + 3),
			SetArgPointee<3>(methodBytesSize),
			Return(S_OK)));

	BYTE buffer[500];
	memset(buffer, 0xFF, sizeof(buffer));

	MockMethodMalloc mockMethodMalloc;
	
	EXPECT_CALL(profilerInfo, GetILFunctionBodyAllocator(_, _))
		.WillOnce(DoAll(
			SetArgPointee<1>(&mockMethodMalloc),
			Return(S_OK)));

	EXPECT_CALL(mockMethodMalloc, Alloc(_))
		.WillOnce(Return(buffer));

	LPCBYTE methodBody;
	EXPECT_CALL(profilerInfo, SetILFunctionBody(_, _, _))
		.WillOnce(DoAll(
			SaveArg<2>(&methodBody),
			Return(S_OK)));

	InstructionList instructions;
	instructions.push_back(new Instruction(CEE_NOP));
	instructions.push_back(new Instruction(CEE_NOP));
	instructions.push_back(new Instruction(CEE_NOP));
	instructions.push_back(new Instruction(CEE_RET));

	ASSERT_EQ(S_OK, injectedType.ReplaceMethodWith(1, 1, instructions));

	// flags, size, max stack
	ASSERT_EQ(0x03, methodBody[0]);
	ASSERT_EQ(0x30, methodBody[1]);
	ASSERT_EQ(0x08, methodBody[2]);
	ASSERT_EQ(0x00, methodBody[3]);
	// code size
	ASSERT_EQ(0x04, methodBody[4]);
	ASSERT_EQ(0x00, methodBody[5]);
	ASSERT_EQ(0x00, methodBody[6]);
	ASSERT_EQ(0x00, methodBody[7]);
	// local function signature
	ASSERT_EQ(0x00, methodBody[8]);
	ASSERT_EQ(0x00, methodBody[9]);
	ASSERT_EQ(0x00, methodBody[10]);
	ASSERT_EQ(0x00, methodBody[11]);
	// instructions
	ASSERT_EQ(CEE_NOP, methodBody[12]);
	ASSERT_EQ(CEE_NOP, methodBody[13]);
	ASSERT_EQ(CEE_NOP, methodBody[14]);
	ASSERT_EQ(CEE_RET, methodBody[15]);
}

TEST_F(InjectedTypeTest, CanReplaceMethodWithLocalVariablesAndCustomStackSize)
{
	MockInjectedType injectedType(profilerInfoPtr, assemblyRegistry);

	const BYTE methodBytes[] = { 0x09, CEE_NOP, CEE_RET };
	LPCBYTE methodBytesStart = &methodBytes[0];
	const auto methodBytesSize = 0x3;

	EXPECT_CALL(profilerInfo, GetILFunctionBody(_, _, _, _))
		.WillOnce(DoAll(
			SetArrayArgument<2>(&methodBytesStart, &methodBytesStart + 3),
			SetArgPointee<3>(methodBytesSize),
			Return(S_OK)));

	BYTE buffer[500];
	memset(buffer, 0xFF, sizeof(buffer));

	MockMethodMalloc mockMethodMalloc;

	EXPECT_CALL(profilerInfo, GetILFunctionBodyAllocator(_, _))
		.WillOnce(DoAll(
			SetArgPointee<1>(&mockMethodMalloc),
			Return(S_OK)));

	EXPECT_CALL(mockMethodMalloc, Alloc(_))
		.WillOnce(Return(buffer));

	LPCBYTE methodBody;
	EXPECT_CALL(profilerInfo, SetILFunctionBody(_, _, _))
		.WillOnce(DoAll(
			SaveArg<2>(&methodBody),
			Return(S_OK)));

	InstructionList instructions;
	instructions.push_back(new Instruction(CEE_NOP));
	instructions.push_back(new Instruction(CEE_NOP));
	instructions.push_back(new Instruction(CEE_NOP));
	instructions.push_back(new Instruction(CEE_RET));

	ASSERT_EQ(S_OK, injectedType.ReplaceMethodWith(1, 1, instructions, 0x01020304, 0x20));

	// flags, size, max stack
	ASSERT_EQ(0x13, methodBody[0]);
	ASSERT_EQ(0x30, methodBody[1]);
	ASSERT_EQ(0x20, methodBody[2]);
	ASSERT_EQ(0x00, methodBody[3]);
	// code size
	ASSERT_EQ(0x04, methodBody[4]);
	ASSERT_EQ(0x00, methodBody[5]);
	ASSERT_EQ(0x00, methodBody[6]);
	ASSERT_EQ(0x00, methodBody[7]);
	// local function signature
	ASSERT_EQ(0x04, methodBody[8]);
	ASSERT_EQ(0x03, methodBody[9]);
	ASSERT_EQ(0x02, methodBody[10]);
	ASSERT_EQ(0x01, methodBody[11]);
	// instructions
	ASSERT_EQ(CEE_NOP, methodBody[12]);
	ASSERT_EQ(CEE_NOP, methodBody[13]);
	ASSERT_EQ(CEE_NOP, methodBody[14]);
	ASSERT_EQ(CEE_RET, methodBody[15]);
}

TEST_F(InjectedTypeTest, CanReplaceMethodWithExceptionHandler)
{
	MockInjectedType injectedType(profilerInfoPtr, assemblyRegistry);

	const BYTE methodBytes[] = { 0x09, CEE_NOP, CEE_RET };
	LPCBYTE methodBytesStart = &methodBytes[0];
	const auto methodBytesSize = 0x3;

	EXPECT_CALL(profilerInfo, GetILFunctionBody(_, _, _, _))
		.WillOnce(DoAll(
			SetArrayArgument<2>(&methodBytesStart, &methodBytesStart + 3),
			SetArgPointee<3>(methodBytesSize),
			Return(S_OK)));

	BYTE buffer[500];
	memset(buffer, 0xFF, sizeof(buffer));

	MockMethodMalloc mockMethodMalloc;

	EXPECT_CALL(profilerInfo, GetILFunctionBodyAllocator(_, _))
		.WillOnce(DoAll(
			SetArgPointee<1>(&mockMethodMalloc),
			Return(S_OK)));

	EXPECT_CALL(mockMethodMalloc, Alloc(_))
		.WillOnce(Return(buffer));

	LPCBYTE methodBody;
	EXPECT_CALL(profilerInfo, SetILFunctionBody(_, _, _))
		.WillOnce(DoAll(
			SaveArg<2>(&methodBody),
			Return(S_OK)));

	InstructionList instructions;
	instructions.push_back(new Instruction(CEE_NOP));
	instructions.push_back(new Instruction(CEE_NOP));
	instructions.push_back(new Instruction(CEE_NOP));
	instructions.push_back(new Instruction(CEE_NOP));
	instructions.push_back(new Instruction(CEE_RET));

	instructions[0]->m_offset = 0;
	instructions[1]->m_offset = 1;
	instructions[2]->m_offset = 2;

	auto exceptionHandler = new ExceptionHandler();
	exceptionHandler->SetTypedHandlerData(0x0100001E,
		instructions[0],
		instructions[1],
		instructions[1],
		instructions[2]);

	ExceptionHandlerList exceptionHandlerList;
	exceptionHandlerList.push_back(exceptionHandler);

	ASSERT_EQ(S_OK, injectedType.ReplaceMethodWith(1, 1, instructions, 0x01020304, 0x20, exceptionHandlerList));

	// flags, size, max stack
	ASSERT_EQ(0x1B, methodBody[0]);
	ASSERT_EQ(0x30, methodBody[1]);
	ASSERT_EQ(0x20, methodBody[2]);
	ASSERT_EQ(0x00, methodBody[3]);
	// code size
	ASSERT_EQ(0x05, methodBody[4]);
	ASSERT_EQ(0x00, methodBody[5]);
	ASSERT_EQ(0x00, methodBody[6]);
	ASSERT_EQ(0x00, methodBody[7]);
	// local function signature
	ASSERT_EQ(0x04, methodBody[8]);
	ASSERT_EQ(0x03, methodBody[9]);
	ASSERT_EQ(0x02, methodBody[10]);
	ASSERT_EQ(0x01, methodBody[11]);
	// instructions
	ASSERT_EQ(CEE_NOP, methodBody[12]);
	ASSERT_EQ(CEE_NOP, methodBody[13]);
	ASSERT_EQ(CEE_NOP, methodBody[14]);
	ASSERT_EQ(CEE_NOP, methodBody[15]);
	ASSERT_EQ(CEE_RET, methodBody[16]);
	ASSERT_EQ(0xFF, methodBody[17]); // padding
	ASSERT_EQ(0xFF, methodBody[18]); // padding
	ASSERT_EQ(0xFF, methodBody[19]); // padding
	// exceptions
	ASSERT_EQ(CorILMethod_Sect_FatFormat + CorILMethod_Sect_EHTable, methodBody[20]); // Section.Kind
	ASSERT_EQ(0x1C, methodBody[21]); // Section.DataSize
	ASSERT_EQ(0x0, methodBody[22]); // Section.DataSize
	ASSERT_EQ(0x0, methodBody[23]); // Section.DataSize
	ASSERT_EQ(0, *(reinterpret_cast<int*>(const_cast<LPBYTE>(methodBody + 24)))); // Handler Type
	ASSERT_EQ(0, *(reinterpret_cast<int*>(const_cast<LPBYTE>(methodBody + 28)))); // Try Begin
	ASSERT_EQ(1, *(reinterpret_cast<int*>(const_cast<LPBYTE>(methodBody + 32)))); // Try Offset
	ASSERT_EQ(1, *(reinterpret_cast<int*>(const_cast<LPBYTE>(methodBody + 36)))); // Handler Begin
	ASSERT_EQ(1, *(reinterpret_cast<int*>(const_cast<LPBYTE>(methodBody + 40)))); // Handler Offset
	ASSERT_EQ(0x0100001E, *(reinterpret_cast<int*>(const_cast<LPBYTE>(methodBody) + 44))); // Token
}

TEST_F(InjectedTypeTest, CanDefineAssemblyMaxVersionRef)
{
	auto publicKeyTokenSize = 8;
	BYTE publicKeyToken[] = { 0, 1, 2, 3, 4, 5, 6, 7 };

	auto assemblyNameSize = 10;
	WCHAR assemblyName[2];
	assemblyName[0] = 'A';
	assemblyName[1] = NULL;

	ASSEMBLYMETADATA metadata1;
	metadata1.usMajorVersion = 1;
	metadata1.usMinorVersion = 0;
	metadata1.usBuildNumber = 6;
	metadata1.usRevisionNumber = 7;

	ASSEMBLYMETADATA metadata2;
	metadata2.usMajorVersion = 1;
	metadata2.usMinorVersion = 2;
	metadata2.usBuildNumber = 3;
	metadata2.usRevisionNumber = 4;

	MockInjectedType injectedType(profilerInfoPtr, assemblyRegistry);

	EXPECT_CALL(metaDataAssemblyImport, GetAssemblyProps(_, _, _, _, _, _, _, _, _))
		.WillOnce(DoAll(
			SetArgPointee<1>(&publicKeyToken),
			SetArgPointee<2>(publicKeyTokenSize),
			SetArgPointee<4>(*assemblyName),
			SetArgPointee<6>(assemblyNameSize),
			SetArgPointee<7>(metadata1),
			Return(S_OK)))
		.WillOnce(DoAll(
			SetArgPointee<1>(&publicKeyToken),
			SetArgPointee<2>(publicKeyTokenSize),
			SetArgPointee<4>(*assemblyName),
			SetArgPointee<6>(assemblyNameSize),
			SetArgPointee<7>(metadata2),
			Return(S_OK)));

	EXPECT_CALL(metaDataAssemblyImport, EnumAssemblyRefs(_, _, _, _))
		.WillRepeatedly(Return(S_FALSE));

	ASSERT_EQ(assemblyRegistry->RecordAssemblyMetadataForModule(1), S_OK);
	ASSERT_EQ(assemblyRegistry->RecordAssemblyMetadataForModule(1), S_OK);

	ASSEMBLYMETADATA metadata;
	EXPECT_CALL(metaDataAssemblyEmit, DefineAssemblyRef(_, _, _, _, _, _, _, _))
		.WillOnce(DoAll(
			SaveArgPointee<3>(&metadata),
			Return(S_OK)));
			
	mdModuleRef moduleRef;
	ASSERT_EQ(S_OK, injectedType.DefineAssemblyMaxVersionRef(1, assemblyName, &moduleRef));

	ASSERT_EQ(1, metadata.usMajorVersion);
	ASSERT_EQ(2, metadata.usMinorVersion);
	ASSERT_EQ(3, metadata.usBuildNumber);
	ASSERT_EQ(4, metadata.usRevisionNumber);
}