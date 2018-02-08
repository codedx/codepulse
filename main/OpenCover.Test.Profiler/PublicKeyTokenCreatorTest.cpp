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

#include "..\OpenCover.Profiler\PublicKeyTokenCreator.h"

using namespace std;
using namespace Injection;

TEST(PublicKeyTokenCreatorTest, CanGeneratorPublicKeyToken)
{
	const DWORD cbPublicKey = 16;
	BYTE publicKey[] = { 00, 00, 00, 00, 00, 00, 00, 00, 04, 00, 00, 00, 00, 00, 00, 00 };

	vector<BYTE> publicKeyToken;

	PublicKeyTokenCreator publicKeyTokenCreator;
	auto result = publicKeyTokenCreator.GetPublicKeyToken(publicKey, cbPublicKey, CALG_SHA1, publicKeyToken);

	ASSERT_TRUE(result);
	ASSERT_EQ(0xB7, publicKeyToken[0]);
	ASSERT_EQ(0x7A, publicKeyToken[1]);
	ASSERT_EQ(0x5C, publicKeyToken[2]);
	ASSERT_EQ(0x56, publicKeyToken[3]);
	ASSERT_EQ(0x19, publicKeyToken[4]);
	ASSERT_EQ(0x34, publicKeyToken[5]);
	ASSERT_EQ(0xE0, publicKeyToken[6]);
	ASSERT_EQ(0x89, publicKeyToken[7]);
}