// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved

using System.Runtime.InteropServices;

namespace CodePulse.Console.EffectiveAccess.Utility
{
    internal static class Win32
    {
        public const int False = 0;
        public const int True = 1;

        public const int MaxPath = 260;
        public const int MaxLongPath = 33000;

        public static bool NT_SUCCESS(int status)
        {
            return status >= 0;
        }

        public const string Advapi32DllFilename = "advapi32.dll";
        public const string AuthzDllFilename = "authz.dll";
        public const string Kernel32DllFilename = "kernel32.dll";
        public const string MprDllFilename = "Mpr.dll";
        public const string Netapi32DllFilename = "Netapi32.dll";
        public const string ShlwapiDllFilename = "shlwapi.dll";

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        public struct Luid
        {
            public uint LowPart;
            public uint HighPart;

            public static Luid NullLuid
            {
                get
                {
                    Luid empty;
                    empty.LowPart = 0;
                    empty.HighPart = 0;

                    return empty;
                }
            }
        }
    }

    internal static class Win32Error
    {
        // Note - the error codes here should all match the definitions in winerror.h.

        /// <summary>
        /// Equal to ERROR_SUCCESS (The operation completed successfully).
        /// </summary>
        public const int NoError = 0;

        /// <summary>
        /// Error code indicating: The operation completed successfully.
        /// </summary>
        public const int ErrorSuccess = 0;

        /// <summary>
        /// The system cannot find the file specified.
        /// </summary>
        public const int ErrorFileNotFound = 2;

        /// <summary>
        /// Error code indicating: Access is denied.
        /// </summary>
        public const int ErrorAccessDenied = 5;

        /// <summary>
        /// Error code indicating: Not enough storage is available to process this command
        /// </summary>
        public const int ErrorNotEnoughMemory = 8;
        /// <summary>
        /// The data area passed to a system call is too small.
        /// </summary>
        public const int ErrorInsufficientBuffer = 122;

        /// <summary>
        /// The filename or extension is too long.
        /// </summary>
        public const int ErrorFilenameExcedRange = 206;

        /// <summary>
        /// More data is available.
        /// </summary>
        public const int ErrorMoreData = 234;

        /// <summary>
        /// An attempt was made to reference a token that does not exist.
        /// </summary>
        public const int ErrorNoToken = 1008;

        /// <summary>
        /// The specified device name is invalid.
        /// </summary>
        public const int ErrorBadDevice = 1200;

        /// <summary>
        /// Not all privileges or groups referenced are assigned to the caller.
        /// </summary>
        public const int ErrorNotAllAssigned = 1300;

        /// <summary>
        /// A specified privilege does not exist.
        /// </summary>
        public const int ErrorNoSuchPrivilege = 1313;

        /// <summary>
        /// Cannot open an anonymous level security token.
        /// </summary>
        public const int ErrorCantOpenAnonymous = 1347;

        /// <summary>
        /// The RPC server is unavailable.
        /// </summary>
        public const int RpcSServerUnavailable = 1722;

        /// <summary>
        /// There are no more endpoints available from the endpoint mapper.
        /// </summary>
        public const int EptSNotRegistered = 1753;

        /// <summary>
        /// This network connection does not exist.
        /// </summary>
        public const int ErrorNotConnected = 2250;
    }
}
