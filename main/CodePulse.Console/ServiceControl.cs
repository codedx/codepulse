// Copyright 2018 Secure Decisions, a division of Applied Visions, Inc. 
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

using System;
using System.Collections.Specialized;
using System.Management;
using System.ServiceProcess;
using Microsoft.Win32;
using OpenCover.Framework.Utility;

namespace CodePulse.Console
{
    public class ServiceControl : IDisposable
    {
        private readonly Service _serviceProxy;
        private readonly ServiceController _service;
        private string _serviceAccountSid;

        public bool InitiallyStarted { get; }

        public string ServiceAccount { get; }

        public bool IsDisabled { get; }

        public string ServiceDisplayName => _serviceProxy.DisplayName;

        public ServiceControl(string serviceName)
        {
            var scope = new ManagementScope();
            scope.Connect();

            var serviceCollection = Service.GetInstances(scope, $"Name = \"{serviceName}\"");
            if (serviceCollection.Count != 1)
            {
                throw new InvalidOperationException($"Expected to find one service with name {serviceName}.");
            }

            var serviceEnumerator = serviceCollection.GetEnumerator();
            serviceEnumerator.MoveNext();

            _serviceProxy = (Service)serviceEnumerator.Current;
            if (_serviceProxy == null)
            {
                throw new InvalidOperationException($"Expected to find one service with name {serviceName} in service collection.");
            }

            _service = new ServiceController(serviceName);

            InitiallyStarted = _serviceProxy.Started;
            IsDisabled = _serviceProxy.StartMode == "Disabled";
            ServiceAccount = _serviceProxy.StartName;     
        }

        public bool StartService(TimeSpan timeout)
        {
            _service.Start();

            try
            {
                _service.WaitForStatus(ServiceControllerStatus.Running, timeout);
                return true;
            }
            catch (System.ServiceProcess.TimeoutException)
            {
                return _service.Status == ServiceControllerStatus.Running;
            }
        }

        public bool StartServiceWithPrincipalBasedEnvironment(TimeSpan timeout, StringDictionary environment)
        {
            if (_serviceAccountSid == null)
            {
                _serviceAccountSid = IdentityHelper.LookupAccountSid(ServiceAccount);
                if (_serviceAccountSid == null)
                {
                    throw new InvalidOperationException($"Unable to find service account SID for{ServiceAccount}.");
                }
            }

            try
            {
                SetAccountEnvironment(environment);

                return StartService(timeout);
            }
            finally
            {
                ResetAccountEnvironment(environment);
            }
        }

        public bool StopService(TimeSpan timeout)
        {
            if (_service.Status == ServiceControllerStatus.Stopped)
            {
                return true;
            }

            _service.Stop();
            try
            {
                _service.WaitForStatus(ServiceControllerStatus.Stopped, timeout);
                return true;
            }
            catch (System.ServiceProcess.TimeoutException)
            {
                return _service.Status == ServiceControllerStatus.Stopped;
            }
        }

        public void WaitForStatus(ServiceControllerStatus status)
        {
            _service.WaitForStatus(status);
        }

        public void Dispose()
        {
            _serviceProxy?.Dispose();
            _service?.Dispose();
        }

        private RegistryKey GetAccountEnvironmentKey()
        {
            var environmentKey = Registry.Users.OpenSubKey($@"{_serviceAccountSid}\Environment", true);
            if (environmentKey == null)
            {
                throw new InvalidOperationException($"Cannot find Environment key for account SID {_serviceAccountSid}.");
            }
            return environmentKey;
        }

        private void SetAccountEnvironment(StringDictionary profilerEnvironment)
        {
            var environmentKey = GetAccountEnvironmentKey();
            foreach (string key in profilerEnvironment.Keys)
            {
                environmentKey.SetValue(key, profilerEnvironment[key]);
            }
        }

        private void ResetAccountEnvironment(StringDictionary profilerEnvironment)
        {
            var environmentKey = GetAccountEnvironmentKey();
            foreach (string key in profilerEnvironment.Keys)
            {
                environmentKey.DeleteValue(key);
            }
        }
    }
}
