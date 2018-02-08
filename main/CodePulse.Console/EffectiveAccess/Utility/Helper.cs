// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved

using System;
using System.Runtime.InteropServices;
using System.Security.Principal;
using System.Text.RegularExpressions;

namespace CodePulse.Console.EffectiveAccess.Utility
{
    using PSECURITY_DESCRIPTOR = IntPtr;

    internal static class Helper
    {
        public static SecurityIdentifier GetSidForObject(string objname, bool device = false)
        {
            if (Regex.Match(objname, @"(S(-\d+){2,8})").Success)
            {
                return new SecurityIdentifier(objname);
            }

            var result = Regex.Match(objname, @"(?<domain>[\w]+)[\\](?:<object>[\w]+)" + (device ? @"\$" : ""));
            var objAccount = result.Success ? new NTAccount(result.Groups["domain"].Value, result.Groups["object"].Value) : new NTAccount(objname);

            return (SecurityIdentifier)objAccount.Translate(typeof(SecurityIdentifier));
        }

        public static byte[] ConvertSecurityDescriptorToByteArray(PSECURITY_DESCRIPTOR securityDescriptor)
        {
            var sdLength = NativeMethods.GetSecurityDescriptorLength(securityDescriptor);

            byte[] buffer = new byte[sdLength];
            Marshal.Copy(securityDescriptor, buffer, 0, (int)sdLength);

            return buffer;
        }
    }
}
