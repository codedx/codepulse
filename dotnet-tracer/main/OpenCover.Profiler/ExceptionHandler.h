//
// OpenCover - S Wilde
//
// This source code is released under the MIT License; see the accompanying license file.
//
// Modified work Copyright 2017 Secure Decisions, a division of Applied Visions, Inc.
//
#include "Instruction.h"

#pragma once

namespace Instrumentation
{
	class ExceptionHandler;
	class Method;

	typedef std::vector<ExceptionHandler *> ExceptionHandlerList;

	/// <summary>A representation of a try/catch section handler</summary>
	class ExceptionHandler
	{
	public:
		ExceptionHandler();

		void SetTypedHandlerData(ULONG exceptionToken, 
			Instruction * tryStart,
			Instruction * tryEnd,
			Instruction * handlerStart,
			Instruction * handlerEnd)
		{
			m_handlerType = COR_ILEXCEPTION_CLAUSE_NONE;
			m_tryStart = tryStart;
			m_tryEnd = tryEnd;
			m_handlerStart = handlerStart;
			m_handlerEnd = handlerEnd;
			m_filterStart = nullptr;
			m_token = exceptionToken;
		}

#ifdef TEST_FRAMEWORK
	public:
#else
	private:
#endif
		CorExceptionFlag m_handlerType;
		Instruction * m_tryStart;
		Instruction * m_tryEnd;
		Instruction * m_handlerStart;
		Instruction * m_handlerEnd;
		Instruction * m_filterStart;

		ULONG m_token;

	public:
		friend class Method;
	};
}