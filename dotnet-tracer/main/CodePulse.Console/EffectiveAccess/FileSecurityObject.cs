// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved

using System.Security.AccessControl;

namespace CodePulse.Console.EffectiveAccess
{
    internal class FileSecurityObject
    {
        public RawSecurityDescriptor SecurityDescriptor;
        public AccessChkResult Result;

        public FileSecurityObject(RawSecurityDescriptor sd)
        {
            SecurityDescriptor = sd;
            Result.GrantedAccess = NativeMethods.FileAccess.None;
        }

        public struct AccessChkResult
        {
            public NativeMethods.FileAccess GrantedAccess;
        };
    }
}
