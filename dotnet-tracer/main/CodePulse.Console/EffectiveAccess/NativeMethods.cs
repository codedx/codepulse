// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved

using System;
using System.Runtime.ConstrainedExecution;
using System.Runtime.InteropServices;
using System.Security;
using CodePulse.Console.EffectiveAccess.Utility;
using Microsoft.Win32.SafeHandles;

namespace CodePulse.Console.EffectiveAccess
{
    using DWORD = UInt32;
    using PSECURITY_DESCRIPTOR = IntPtr;

    internal static class NativeMethods
    {
        #region authz
        [StructLayout(LayoutKind.Sequential)]
        internal struct AuthzAccessRequest
        {
            public StdAccess DesiredAccess;
            public byte[] PrincipalSelfSid;
            public IntPtr ObjectTypeList;
            public int ObjectTypeListLength;
            public IntPtr OptionalArguments;
        }

        [StructLayout(LayoutKind.Sequential)]
        internal struct AuthzAccessReply
        {
            public int ResultListLength;
            public IntPtr GrantedAccessMask;
            public IntPtr SaclEvaluationResults;
            public IntPtr Error;
        }

        internal enum AuthzAcFlags : uint // DWORD
        {
            None = 0,
            NoDeepCopySd
        }

        [DllImport(Win32.AuthzDllFilename, CharSet = CharSet.Unicode, SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool AuthzAccessCheck(
            AuthzAcFlags flags,
            IntPtr hAuthzClientContext,
            ref AuthzAccessRequest pRequest,
            IntPtr auditEvent,
            byte[] rawSecurityDescriptor,
            IntPtr[] optionalSecurityDescriptorArray,
            UInt32 optionalSecurityDescriptorCount,
            ref AuthzAccessReply pReply,
            IntPtr cachedResults);

        [DllImport(Win32.AuthzDllFilename, CharSet = CharSet.Unicode, ExactSpelling = true, SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool AuthzFreeContext(IntPtr authzClientContext);

        [DllImport(Win32.AuthzDllFilename, SetLastError = true),
         SuppressUnmanagedCodeSecurity,
         ReliabilityContract(Consistency.WillNotCorruptState, Cer.Success)]
        [return: MarshalAs(UnmanagedType.Bool)]
        public static extern bool AuthzFreeResourceManager(IntPtr handle);

        internal enum AuthzRpcClientVersion : ushort // USHORT
        {
            V1 = 1
        }

        internal const string AuthzObjectUuidWithcap = "9a81c2bd-a525-471d-a4ed-49907c0b23da";

        internal const string RcpOverTcpProtocol = "ncacn_ip_tcp";

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        internal struct AuthzRpcInitInfoClient
        {
            public AuthzRpcClientVersion version;
            public string objectUuid;
            public string protocol;
            public string server;
            public string endPoint;
            public string options;
            public string serverSpn;
        }

        [DllImport(Win32.AuthzDllFilename, CharSet = CharSet.Unicode, SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool AuthzInitializeRemoteResourceManager(
            IntPtr rpcInitInfo,
            out SafeAuthzRMHandle authRm);

        [Flags]
        internal enum AuthzInitFlags : uint
        {
            Default = 0x0,
            SkipTokenGroups = 0x2,
            RequireS4ULogon = 0x4,
            ComputePrivileges = 0x8,
        }

        [DllImport(Win32.AuthzDllFilename, CharSet = CharSet.Unicode, SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool AuthzInitializeContextFromSid(
            AuthzInitFlags flags,
            byte[] rawUserSid,
            SafeAuthzRMHandle authzRm,
            IntPtr expirationTime,
            Win32.Luid identifier,
            IntPtr dynamicGroupArgs,
            out IntPtr authzClientContext);

        [DllImport(Win32.AuthzDllFilename, CharSet = CharSet.Unicode, SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool AuthzInitializeCompoundContext(
            IntPtr userClientContext,
            IntPtr deviceClientContext,
            out IntPtr compoundContext);

        [Flags]
        internal enum AuthzResourceManagerFlags : uint
        {
            NoAudit = 0x1,
        }

        [DllImport(Win32.AuthzDllFilename, CharSet = CharSet.Unicode, ExactSpelling = true, SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        internal static extern bool AuthzInitializeResourceManager(
            AuthzResourceManagerFlags flags,
            IntPtr pfnAccessCheck,
            IntPtr pfnComputeDynamicGroups,
            IntPtr pfnFreeDynamicGroups,
            string szResourceManagerName,
            out SafeAuthzRMHandle phAuthzResourceManager);

        #endregion

        #region PInvoke kernel32
        [Flags]
        internal enum StdAccess : uint
        {
            None = 0x0,

            Synchronize = 0x100000,
            StandardRightsRequired = 0xF0000,

            MaximumAllowed = 0x2000000,
        }

        [Flags]
        internal enum FileAccess : uint
        {
            None = 0x0,
            ReadData = 0x1,
            WriteData = 0x2,
            AppendData = 0x4,
            ReadExAttrib = 0x8,
            WriteExAttrib = 0x10,
            Execute = 0x20,
            DeleteChild = 0x40,
            ReadAttrib = 0x80,
            WriteAttrib = 0x100,

            Delete = 0x10000,   // DELETE,
            ReadPermissions = 0x20000,   // READ_CONTROL
            ChangePermissions = 0x40000,   // WRITE_DAC,
            TakeOwnership = 0x80000,   // WRITE_OWNER,

            GenericRead = ReadPermissions
                        | ReadData
                        | ReadAttrib
                        | ReadExAttrib
                        | StdAccess.Synchronize,

            GenericAll = StdAccess.StandardRightsRequired | 0x1FF,

            CategoricalAll = uint.MaxValue
        }

        [Flags]
        internal enum FileShare : uint
        {
            None = 0x0,
            Read = 0x1,
            Write = 0x2,
            Delete = 0x4
        }

        internal enum FileMode : uint
        {
            OpenExisting = 3,
        }

        [Flags]
        internal enum FileFlagAttrib : uint
        {
            BackupSemantics = 0x02000000,
        }

        [DllImport(Win32.Kernel32DllFilename, SetLastError = true, CharSet = CharSet.Unicode)]
        internal static extern SafeFileHandle CreateFile(string lpFileName,
                                                         FileAccess desiredAccess,
                                                         FileShare shareMode,
                                                         IntPtr lpSecurityAttributes,
                                                         FileMode mode,
                                                         FileFlagAttrib flagsAndAttributes,
                                                         IntPtr hTemplateFile);
        #endregion

        #region PInvoke advapi32
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
        internal struct AceHeader
        {
            public byte AceType;
            public byte AceFlags;
            public ushort AceSize;
        }

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
        internal struct SystemScopedPolicyIdAce
        {
            public AceHeader Header;
            public uint Mask;
            public uint SidStart;
        }

        internal enum ObjectType : uint
        {
            File = 1
        }

        [Flags]
        internal enum SecurityInformationClass : uint
        {
            Owner = 0x00001,
            Group = 0x00002,
            Dacl = 0x00004,
            Sacl = 0x00008,
            Label = 0x00010,
            Attribute = 0x00020,
            Scope = 0x00040
        }

        [DllImport(Win32.Advapi32DllFilename, CallingConvention = CallingConvention.Winapi, SetLastError = true, CharSet = CharSet.Unicode)]
        internal static extern UInt32 GetSecurityInfo(
            SafeFileHandle handle,
            ObjectType objectType,
            SecurityInformationClass infoClass,
            IntPtr owner,
            IntPtr group,
            IntPtr dacl,
            IntPtr sacl,
            out IntPtr securityDescriptor);

        [DllImport(Win32.Advapi32DllFilename, CallingConvention = CallingConvention.Winapi, CharSet = CharSet.Unicode)]
        internal static extern DWORD GetSecurityDescriptorLength(PSECURITY_DESCRIPTOR pSecurityDescriptor);
        #endregion
    }
}
