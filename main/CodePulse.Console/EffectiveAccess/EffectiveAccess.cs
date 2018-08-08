// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved

using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Security.AccessControl;
using CodePulse.Console.EffectiveAccess.Utility;

namespace CodePulse.Console.EffectiveAccess
{
    using LPVOID = IntPtr;
    using PDWORD = IntPtr;
    
    using PLARGE_INTEGER = IntPtr;
    using PSECURITY_DESCRIPTOR = IntPtr;
    using AUTHZ_CLIENT_CONTEXT_HANDLE = IntPtr;
    using AUTHZ_AUDIT_EVENT_HANDLE = IntPtr;
    using AUTHZ_ACCESS_CHECK_RESULTS_HANDLE = IntPtr;
    using POBJECT_TYPE_LIST = IntPtr;
    using PACCESS_MASK = IntPtr;

    public class EffectiveAccess
    {
        private readonly int _grantedAccessMask;

        public string Path { get; }

        public string Username { get; }

        public bool HasReadAccess
        {
            get
            {
                const int genericRead = (int) NativeMethods.FileAccess.GenericRead;
                return (_grantedAccessMask & genericRead) == genericRead;
            }
        }

        public bool HasExecuteAccess
        {
            get
            {
                const int execute = (int)NativeMethods.FileAccess.Execute;
                return (_grantedAccessMask & execute) == execute;
            }
        }

        public bool HasReadAndExecuteAccess => HasReadAccess && HasExecuteAccess;

        public EffectiveAccess(string path, string username)
        {
            Path = path;
            Username = username;

            _grantedAccessMask = GetGrantedAccessMask();
        }

        private int GetGrantedAccessMask()
        {
            var userSid = Helper.GetSidForObject(Username);

            var handle = NativeMethods.CreateFile(Path,
                NativeMethods.FileAccess.GenericRead,
                NativeMethods.FileShare.Read
                | NativeMethods.FileShare.Write
                | NativeMethods.FileShare.Delete,
                IntPtr.Zero,
                NativeMethods.FileMode.OpenExisting,
                NativeMethods.FileFlagAttrib.BackupSemantics,
                IntPtr.Zero);

            if (handle.IsInvalid)
            {
                throw new Win32Exception(Marshal.GetLastWin32Error());
            }


            RawSecurityDescriptor fileSd;
            var tempSd = PSECURITY_DESCRIPTOR.Zero;
            try
            {
                var error = NativeMethods.GetSecurityInfo(handle,
                    NativeMethods.ObjectType.File,
                    NativeMethods.SecurityInformationClass.Owner
                    | NativeMethods.SecurityInformationClass.Group
                    | NativeMethods.SecurityInformationClass.Dacl
                    | NativeMethods.SecurityInformationClass.Label
                    | NativeMethods.SecurityInformationClass.Attribute,
                    IntPtr.Zero, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero,
                    out tempSd);

                if (error != Win32Error.ErrorSuccess)
                {
                    throw new Win32Exception(Marshal.GetLastWin32Error());
                }

                fileSd = new RawSecurityDescriptor(Helper.ConvertSecurityDescriptorToByteArray(tempSd), 0);
            }
            finally
            {
                Marshal.FreeHGlobal(tempSd);
            }

            var fso = new FileSecurityObject(fileSd);

            SafeAuthzRMHandle authzRm = null;
            var userClientCtxt = AUTHZ_CLIENT_CONTEXT_HANDLE.Zero;
            try
            {
                if (!NativeMethods.AuthzInitializeResourceManager(
                    NativeMethods.AuthzResourceManagerFlags.NoAudit,
                    IntPtr.Zero, IntPtr.Zero, IntPtr.Zero, null,
                    out authzRm))
                {
                    throw new Win32Exception(Marshal.GetLastWin32Error());
                }

                var rawSid = new byte[userSid.BinaryLength];
                userSid.GetBinaryForm(rawSid, 0);

                if (!NativeMethods.AuthzInitializeContextFromSid(NativeMethods.AuthzInitFlags.Default,
                                                                 rawSid,
                                                                 authzRm,
                                                                 PLARGE_INTEGER.Zero,
                                                                 Win32.Luid.NullLuid,
                                                                 LPVOID.Zero,
                                                                 out userClientCtxt))
                {
                    throw new Win32Exception(Marshal.GetLastWin32Error());
                }

                var grantedAccess = new PACCESS_MASK[1];
                var errorSecObj = new PACCESS_MASK[1];

                var request = new NativeMethods.AuthzAccessRequest
                {
                    DesiredAccess = NativeMethods.StdAccess.MaximumAllowed,
                    PrincipalSelfSid = null,
                    ObjectTypeList = POBJECT_TYPE_LIST.Zero,
                    ObjectTypeListLength = 0,
                    OptionalArguments = LPVOID.Zero
                };

                var reply = new NativeMethods.AuthzAccessReply
                {
                    ResultListLength = 1,
                    SaclEvaluationResults = PDWORD.Zero,
                    GrantedAccessMask = grantedAccess[0] = Marshal.AllocHGlobal(sizeof(uint)),
                    Error = errorSecObj[0] = Marshal.AllocHGlobal(sizeof(uint))
                };

                var rawSd = new byte[fso.SecurityDescriptor.BinaryLength];
                fso.SecurityDescriptor.GetBinaryForm(rawSd, 0);

                if (!NativeMethods.AuthzAccessCheck(NativeMethods.AuthzAcFlags.None,
                    userClientCtxt,
                    ref request,
                    AUTHZ_AUDIT_EVENT_HANDLE.Zero,
                    rawSd,
                    null,
                    0,
                    ref reply,
                    AUTHZ_ACCESS_CHECK_RESULTS_HANDLE.Zero))
                {
                    throw new Win32Exception(Marshal.GetLastWin32Error());
                }

                return Marshal.ReadInt32(reply.GrantedAccessMask);
            }
            finally
            {
                if (userClientCtxt != AUTHZ_CLIENT_CONTEXT_HANDLE.Zero)
                {
                    NativeMethods.AuthzFreeContext(userClientCtxt);
                }

                authzRm?.Dispose();
            }
        }
    }
}
