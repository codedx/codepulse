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
#include "MockICorProfilerInfo.h"
#include "MockIMetaDataAssemblyImport.h"

using namespace Injection;
using namespace testing;
using namespace std;

class AssemblyRegistryTest : public Test 
{
protected:
	AssemblyRegistryTest() :
		Test(), 
		profilerInfoPtr(&profilerInfo), 
		assemblyRegistry(profilerInfoPtr)
	{
	}

	void SetUp() override
	{
		EXPECT_CALL(profilerInfo, GetModuleMetaData(_, _, IID_IMetaDataAssemblyImport, _))
			.WillRepeatedly(DoAll(SetArgPointee<3>(&metaDataAssemblyImport), Return(S_OK)));
	}

	void TearDown() override
	{
	}

	void TestVersionComparison(
		int metadataOneMajorVersion, int metadataOneMinorVersion, int metadataOneBuildNumber, int metadataOneRevisionNumber,
		int metadataTwoMajorVersion, int metadataTwoMinorVersion, int metadataTwoBuildNumber, int metadataTwoRevisionNumber,
		int assertMajorVersion, int assertMinorVersion, int assertBuildNumber, int assertRevisionNumber)
	{
		auto publicKeyTokenSize = 8;
		BYTE publicKeyToken[] = { 0, 1, 2, 3, 4, 5, 6, 7 };

		auto assemblyNameSize = 10;
		WCHAR assemblyName[2];
		assemblyName[0] = 'A';
		assemblyName[1] = NULL;

		ASSEMBLYMETADATA metadata1;
		metadata1.usMajorVersion = metadataOneMajorVersion;
		metadata1.usMinorVersion = metadataOneMinorVersion;
		metadata1.usBuildNumber = metadataOneBuildNumber;
		metadata1.usRevisionNumber = metadataOneRevisionNumber;

		ASSEMBLYMETADATA metadata2;
		metadata2.usMajorVersion = metadataTwoMajorVersion;
		metadata2.usMinorVersion = metadataTwoMinorVersion;
		metadata2.usBuildNumber = metadataTwoBuildNumber;
		metadata2.usRevisionNumber = metadataTwoRevisionNumber;

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

		ASSERT_EQ(assemblyRegistry.RecordAssemblyMetadataForModule(1), S_OK);
		ASSERT_EQ(assemblyRegistry.RecordAssemblyMetadataForModule(1), S_OK);

		AssemblyVersion assemblyVersion;
		ASSERT_TRUE(assemblyRegistry.FindMaxAssemblyVersion(assemblyName, assemblyVersion));
		AssertEqual(assemblyVersion,
			assertMajorVersion, assertMinorVersion, assertBuildNumber, assertRevisionNumber);

		AssemblyReference assemblyReference;
		ASSERT_TRUE(assemblyRegistry.FindMaxAssemblyVersion(assemblyName, assemblyReference));
		AssertEqual(assemblyReference.version,
			assertMajorVersion, assertMinorVersion, assertBuildNumber, assertRevisionNumber);
	}

	void AssertEqual(const AssemblyReference& assemblyReference,
		const LPWSTR name,
		const int majorVersion, const int minorVersion, const int buildNumber, const int revisionNumber,
		BYTE pkt0, BYTE pkt1, BYTE pkt2, BYTE pkt3, BYTE pkt4, BYTE pkt5, BYTE pkt6, BYTE pkt7) const
	{
		ASSERT_EQ(name, assemblyReference.name);
		ASSERT_EQ(pkt0, assemblyReference.publicKeyToken[0]);
		ASSERT_EQ(pkt1, assemblyReference.publicKeyToken[1]);
		ASSERT_EQ(pkt2, assemblyReference.publicKeyToken[2]);
		ASSERT_EQ(pkt3, assemblyReference.publicKeyToken[3]);
		ASSERT_EQ(pkt4, assemblyReference.publicKeyToken[4]);
		ASSERT_EQ(pkt5, assemblyReference.publicKeyToken[5]);
		ASSERT_EQ(pkt6, assemblyReference.publicKeyToken[6]);
		ASSERT_EQ(pkt7, assemblyReference.publicKeyToken[7]);

		AssertEqual(assemblyReference.version,
			majorVersion, minorVersion, buildNumber, revisionNumber);
	}

	void AssertEqual(const AssemblyVersion& assemblyVersion,
		const int majorVersion, const int minorVersion, const int buildNumber, const int revisionNumber) const
	{
		ASSERT_EQ(majorVersion, assemblyVersion.majorVersion);
		ASSERT_EQ(minorVersion, assemblyVersion.minorVersion);
		ASSERT_EQ(buildNumber, assemblyVersion.buildNumber);
		ASSERT_EQ(revisionNumber, assemblyVersion.revisionNumber);
	}

	MockICorProfilerInfo profilerInfo;
	CComPtr<ICorProfilerInfo> profilerInfoPtr;

	MockIMetaDataAssemblyImport metaDataAssemblyImport;

	AssemblyRegistry assemblyRegistry;
};


TEST_F(AssemblyRegistryTest, CanRecordAssemblyMetadataForOneModule)
{
	auto publicKeyTokenSize = 8;
	BYTE publicKeyToken[] = { 0, 1, 2, 3, 4, 5, 6, 7 };
	
	auto assemblyNameSize = 1;
	WCHAR assemblyName[2];
	assemblyName[0] = 'A';
	assemblyName[1] = NULL;
	
	ASSEMBLYMETADATA metadata;
	metadata.usMajorVersion = 4;
	metadata.usMinorVersion= 3;
	metadata.usBuildNumber = 2;
	metadata.usRevisionNumber = 1;

	EXPECT_CALL(metaDataAssemblyImport, GetAssemblyProps(_, _, _, _, _, _, _, _, _))
		.WillOnce(DoAll(
			SetArgPointee<1>(&publicKeyToken), 
			SetArgPointee<2>(publicKeyTokenSize),
			SetArgPointee<4>(*assemblyName),
			SetArgPointee<6>(assemblyNameSize),
			SetArgPointee<7>(metadata),
			Return(S_OK)));

	EXPECT_CALL(metaDataAssemblyImport, EnumAssemblyRefs(_, _, _, _))
		.WillRepeatedly(Return(S_FALSE));
	
	ASSERT_EQ(assemblyRegistry.RecordAssemblyMetadataForModule(1), S_OK);
	
	vector<AssemblyReference> references;
	ASSERT_TRUE(assemblyRegistry.FillAssembliesByName(assemblyName, references));

	ASSERT_EQ(references.size(), 1);
	AssertEqual(references[0],
		L"A",
		4, 3, 2, 1,
		0, 1, 2, 3, 4, 5, 6, 7);
}

TEST_F(AssemblyRegistryTest, CanRecordAssemblyMetadataForTwoVersionsOfOneModule)
{
	auto publicKeyTokenSize = 8;
	BYTE publicKeyToken[] = { 0, 1, 2, 3, 4, 5, 6, 7 };

	auto assemblyNameSize = 1;
	WCHAR assemblyName[2];
	assemblyName[0] = 'A';
	assemblyName[1] = NULL;

	ASSEMBLYMETADATA metadata1;
	metadata1.usMajorVersion = 4;
	metadata1.usMinorVersion = 3;
	metadata1.usBuildNumber = 2;
	metadata1.usRevisionNumber = 1;

	ASSEMBLYMETADATA metadata2;
	metadata2.usMajorVersion = 8;
	metadata2.usMinorVersion = 6;
	metadata2.usBuildNumber = 4;
	metadata2.usRevisionNumber = 2;

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

	ASSERT_EQ(assemblyRegistry.RecordAssemblyMetadataForModule(1), S_OK);
	ASSERT_EQ(assemblyRegistry.RecordAssemblyMetadataForModule(1), S_OK);

	vector<AssemblyReference> references;
	ASSERT_TRUE(assemblyRegistry.FillAssembliesByName(assemblyName, references));

	ASSERT_EQ(references.size(), 2);
	AssertEqual(references[0],
		L"A",
		4, 3, 2, 1,
		0, 1, 2, 3, 4, 5, 6, 7);
	AssertEqual(references[1],
		L"A",
		8, 6, 4, 2,
		0, 1, 2, 3, 4, 5, 6, 7);
}

TEST_F(AssemblyRegistryTest, CanRecordAssemblyMetadataForTwoModules)
{
	auto publicKeyTokenSize = 8;
	BYTE publicKeyToken[] = { 0, 1, 2, 3, 4, 5, 6, 7 };

	auto assemblyNameSize = 1;
	WCHAR assemblyName1[2];
	assemblyName1[0] = 'A';
	assemblyName1[1] = NULL;

	WCHAR assemblyName2[2];
	assemblyName2[0] = 'B';
	assemblyName2[1] = NULL;

	ASSEMBLYMETADATA metadata;
	metadata.usMajorVersion = 4;
	metadata.usMinorVersion = 3;
	metadata.usBuildNumber = 2;
	metadata.usRevisionNumber = 1;

	EXPECT_CALL(metaDataAssemblyImport, GetAssemblyProps(_, _, _, _, _, _, _, _, _))
		.WillOnce(DoAll(
			SetArgPointee<1>(&publicKeyToken),
			SetArgPointee<2>(publicKeyTokenSize),
			SetArgPointee<4>(*assemblyName1),
			SetArgPointee<6>(assemblyNameSize),
			SetArgPointee<7>(metadata),
			Return(S_OK)))
		.WillOnce(DoAll(
			SetArgPointee<1>(&publicKeyToken),
			SetArgPointee<2>(publicKeyTokenSize),
			SetArgPointee<4>(*assemblyName2),
			SetArgPointee<6>(assemblyNameSize),
			SetArgPointee<7>(metadata),
			Return(S_OK)));

	EXPECT_CALL(metaDataAssemblyImport, EnumAssemblyRefs(_, _, _, _))
		.WillRepeatedly(Return(S_FALSE));

	ASSERT_EQ(assemblyRegistry.RecordAssemblyMetadataForModule(1), S_OK);
	ASSERT_EQ(assemblyRegistry.RecordAssemblyMetadataForModule(1), S_OK);

	vector<AssemblyReference> references;
	ASSERT_TRUE(assemblyRegistry.FillAssembliesByName(assemblyName1, references));
	ASSERT_EQ(references.size(), 1);
	AssertEqual(references[0],
		L"A",
		4, 3, 2, 1,
		0, 1, 2, 3, 4, 5, 6, 7);

	references.clear();
	ASSERT_TRUE(assemblyRegistry.FillAssembliesByName(assemblyName2, references));
	ASSERT_EQ(references.size(), 1);
	AssertEqual(references[0],
		L"B",
		4, 3, 2, 1,
		0, 1, 2, 3, 4, 5, 6, 7);
}

TEST_F(AssemblyRegistryTest, CanFindMaxVersionForTwoVersionsOfOneModule)
{
	TestVersionComparison(
		4, 3, 2, 1,
		8, 6, 4, 2,
		8, 6, 4, 2);
}

TEST_F(AssemblyRegistryTest, CanFindMaxMajorVersionForTwoVersionsOfOneModule)
{
	TestVersionComparison(
		4, 3, 2, 1,
		5, 3, 2, 1,
		5, 3, 2, 1);
}

TEST_F(AssemblyRegistryTest, CanFindMaxMinorVersionForTwoVersionsOfOneModule)
{
	TestVersionComparison(
		4, 4, 2, 1,
		4, 3, 2, 1,
		4, 4, 2, 1);
}

TEST_F(AssemblyRegistryTest, CanFindMaxBuildNumberForTwoVersionsOfOneModule)
{
	TestVersionComparison(
		4, 3, 2, 1,
		4, 3, 3, 1,
		4, 3, 3, 1);
}

TEST_F(AssemblyRegistryTest, CanFindMaxRevisionNumberForTwoVersionsOfOneModule)
{
	TestVersionComparison(
		4, 3, 2, 2,
		4, 3, 2, 1,
		4, 3, 2, 2);
}

TEST_F(AssemblyRegistryTest, SearchingForUnknownAssemblyReturnsFalse)
{
	wchar_t* assemblyName = L"?";
	vector<AssemblyReference> references;
	ASSERT_FALSE(assemblyRegistry.FillAssembliesByName(assemblyName, references));
	ASSERT_EQ(0, references.size());
}

TEST_F(AssemblyRegistryTest, SearchingForUnknownAssemblyMaxVersionReturnsFalse)
{
	wchar_t* assemblyName = L"?";
	AssemblyVersion assemblyVersion;
	ASSERT_FALSE(assemblyRegistry.FindMaxAssemblyVersion(assemblyName, assemblyVersion));
}

TEST_F(AssemblyRegistryTest, CanRecordAssemblyMetadataForOneModuleAndReference)
{
	auto publicKeyTokenSize = 8;
	BYTE publicKeyToken[] = { 0, 1, 2, 3, 4, 5, 6, 7 };

	auto assemblyNameSize = 1;
	WCHAR assemblyName1[2];
	assemblyName1[0] = 'A';
	assemblyName1[1] = NULL;

	ASSEMBLYMETADATA metadata1;
	metadata1.usMajorVersion = 4;
	metadata1.usMinorVersion = 3;
	metadata1.usBuildNumber = 2;
	metadata1.usRevisionNumber = 1;

	EXPECT_CALL(metaDataAssemblyImport, GetAssemblyProps(_, _, _, _, _, _, _, _, _))
		.WillOnce(DoAll(
			SetArgPointee<1>(&publicKeyToken),
			SetArgPointee<2>(publicKeyTokenSize),
			SetArgPointee<4>(*assemblyName1),
			SetArgPointee<6>(assemblyNameSize),
			SetArgPointee<7>(metadata1),
			Return(S_OK)));

	WCHAR assemblyName2[2];
	assemblyName2[0] = 'B';
	assemblyName2[1] = NULL;

	ASSEMBLYMETADATA metadata2;
	metadata2.usMajorVersion = 14;
	metadata2.usMinorVersion = 13;
	metadata2.usBuildNumber = 12;
	metadata2.usRevisionNumber = 11;

	mdAssemblyRef refs[1];
	refs[0] = 0;
	ULONG refsCount = 1;
	EXPECT_CALL(metaDataAssemblyImport, EnumAssemblyRefs(_, _, _, _))
		.WillOnce(DoAll(
			SetArgPointee<1>(*refs),
			SetArgPointee<3>(refsCount),
			Return(S_OK)))
		.WillOnce(Return(S_FALSE));

	EXPECT_CALL(metaDataAssemblyImport, GetAssemblyRefProps(_, _, _, _, _, _, _, _, _, _))
		.WillOnce(DoAll(
			SetArgPointee<1>(&publicKeyToken),
			SetArgPointee<2>(publicKeyTokenSize),
			SetArgPointee<3>(*assemblyName2),
			SetArgPointee<5>(assemblyNameSize),
			SetArgPointee<6>(metadata2),
			Return(S_OK)));

	ASSERT_EQ(assemblyRegistry.RecordAssemblyMetadataForModule(1), S_OK);

	vector<AssemblyReference> references;
	ASSERT_TRUE(assemblyRegistry.FillAssembliesByName(assemblyName2, references));

	wchar_t* assemblyName = L"B";
	ASSERT_EQ(references.size(), 1);
	AssertEqual(references[0],
		assemblyName,
		14, 13, 12, 11,
		0, 1, 2, 3, 4, 5, 6, 7);
}