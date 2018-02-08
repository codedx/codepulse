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

#include "PublicKeyTokenCreator.h"

namespace Injection
{
	PublicKeyTokenCreator::PublicKeyTokenCreator()
	{
		m_hCryptProv = 0;

		if (!CryptAcquireContext(
			&m_hCryptProv,
			nullptr,
			nullptr,
			PROV_RSA_FULL,
			CRYPT_VERIFYCONTEXT))
		{
			m_hCryptProv = 0;
			return;
		}
	}

	PublicKeyTokenCreator::~PublicKeyTokenCreator()
	{
		if (m_hCryptProv == 0)
		{
			return;
		}
		CryptReleaseContext(m_hCryptProv, 0);
	}

	bool PublicKeyTokenCreator::GetPublicKeyToken(BYTE* pbPublicKey, const DWORD cbPublicKey, const ALG_ID hashAlgorithmId,
		std::vector<BYTE>& publicKeyToken) const
	{
		if (m_hCryptProv == 0)
		{
			return false;
		}

		if (pbPublicKey == nullptr)
		{
			return false;
		}
		publicKeyToken.clear();

		HCRYPTHASH hHash;
		if (!CryptCreateHash(
			m_hCryptProv,
			hashAlgorithmId,
			0,
			0,
			&hHash))
		{
			return false;
		}

		if (!CryptHashData(hHash, pbPublicKey, cbPublicKey, 0))
		{
			return false;
		}

		const auto hashBufferSize = 150;
		BYTE hashBuffer[hashBufferSize];
		DWORD cbHashBuffer = hashBufferSize;

		const auto result = CryptGetHashParam(hHash,
			HP_HASHVAL,
			hashBuffer,
			&cbHashBuffer, 0);

		if (result && cbHashBuffer >= 8)
		{
			const auto lastIndex = cbHashBuffer - 8;
			for (auto index = cbHashBuffer - 1; index >= lastIndex; index--)
			{
				publicKeyToken.push_back(hashBuffer[index]);
			}
		}

		CryptDestroyHash(hHash);
		return result;
	}
}