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

//
// OpenCover - S Wilde
//
// This source code is released under the MIT License; see the accompanying license file.
//
using System;
using System.Collections.Generic;
using System.Collections.Specialized;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Security.AccessControl;
using System.Security.Principal;
using System.ServiceProcess;
using System.Threading.Tasks;
using OpenCover.Framework;
using OpenCover.Framework.Manager;
using OpenCover.Framework.Persistance;
using OpenCover.Framework.Utility;
using log4net;
using CodePulse.Client.Config;
using CodePulse.Framework.Persistence;
using OpenCover.Framework.Model;
using File = System.IO.File;

namespace CodePulse.Console
{
    internal class ProgramExitCodes
    {
        public const int Success = 0;
        public const int CannotParseCommandLine = 1;
        public const int CannotInitializeCodePulseConnection = 2;
        public const int ApplicationExitDueToError = 3;
        public const int ApplicationExitDueToUnexpectedException = 4;
        public const int ApplicationExitDueToUnhandledException = 5;
        public const int RunProcessFailed = 6;
        public const int CannotStopServiceBeforeTrace = 7;
        public const int ServiceFailedToStart = 8;
        public const int CannotRestoreServiceStatus = 9;
        public const int IisWebApplicationProfilingAlreadyRunning = 10;
        public const int CannotStopServiceAfterTrace = 11;
        public const int ProfilerNoReadPermission = 12;
    }

    internal class Program
    {
        private static readonly ILog Logger = LogManager.GetLogger("OpenCover");

        private static int _returnCodeOffset;

        private const string WorldWideWebPublishingServiceName = "w3svc";
        private const string WorldWideWebPublishingServiceDisplayName = "World Wide Web Publishing Service";
        private const string WindowsProcessActivationServiceName = "was";

        private const string ServiceApplicationProfilingKey = "1e830ab5-10e3-4201-9d39-fca143b77d8f";

        private const string GitHubIssuesListUrl = "https://github.com/codedx/codepulse/issues";
        private const string GitHubIssuesStatement = "If you are unable to resolve the issue, please contact the Code Pulse development team";

        private static CodePulsePersistence _persistence;

        /// <summary>
        /// This is the Code Pulse .NET Tracer application.
        /// </summary>
        /// <param name="args">Application arguments - see usage statement</param>
        /// <returns>Return code adjusted by optional offset.</returns>
        private static int Main(string[] args)
        {
            AppDomain.CurrentDomain.UnhandledException += CurrentDomainOnUnhandledException;

            try
            {
                if (!ParseCommandLine(args, out var parser))
                {
                    return ProgramExitCodes.CannotParseCommandLine;
                }
                _returnCodeOffset = parser.ReturnCodeOffset;

                LogManager.GetRepository().Threshold = parser.LogLevel;

                if (!DoesUserHaveReadAndExecuteAccess(parser.Profiler32Path, parser.ExpectedOwnerOfApplicationUnderTest, out var new32BitAccessRule))
                {
                    LogMandatoryFatal($"The application cannot start because expected owner of the application under test ({parser.ExpectedOwnerOfApplicationUnderTest}) does not have read and execute permissions for the 32-bit profiler library ({parser.Profiler32Path}).");
                    return MakeExitCode(ProgramExitCodes.ProfilerNoReadPermission);
                }

                try
                {
                    FileSystemAccessRule new64BitAccessRule = null;
                    if (Environment.Is64BitOperatingSystem && !DoesUserHaveReadAndExecuteAccess(parser.Profiler64Path, parser.ExpectedOwnerOfApplicationUnderTest, out new64BitAccessRule))
                    {
                        LogMandatoryFatal($"The application cannot start because expected owner of the application under test ({parser.ExpectedOwnerOfApplicationUnderTest}) does not have read and execute permissions for the 64-bit profiler library ({parser.Profiler64Path}).");
                        return MakeExitCode(ProgramExitCodes.ProfilerNoReadPermission);
                    }

                    try
                    {
                        Logger.Debug($"32-bit Profiler Path: {parser.Profiler32Path}");
                        if (Environment.Is64BitOperatingSystem)
                        {
                            Logger.Debug($"64-bit Profiler Path: {parser.Profiler64Path}");
                        }
                        Logger.Debug($"Expected owner of application under test: {parser.ExpectedOwnerOfApplicationUnderTest}");

                        Logger.Info("Starting...");

                        var filter = BuildFilter(parser);
                        var perfCounter = CreatePerformanceCounter(parser);

                        using (var container = new Bootstrapper(Logger))
                        {
                            Logger.Info("Connecting to Code Pulse...");
                            LogMandatoryInfo("Open Code Pulse, select a project, wait for the connection, and start a trace.");

                            _persistence = new CodePulsePersistence(parser, Logger);
                            container.Initialise(filter, parser, _persistence, perfCounter);
                            if (!_persistence.Initialize(new StaticAgentConfiguration(parser.CodePulsePort, parser.CodePulseHost, parser.CodePulseConnectTimeout, Logger)))
                            {
                                LogMandatoryFatal("Failed to initialize Code Pulse connection. Is Code Pulse running?");
                                return MakeExitCode(ProgramExitCodes.CannotInitializeCodePulseConnection);
                            }

                            var returnCode = RunWithContainer(parser, container, _persistence);

                            perfCounter.ResetCounters();

                            return returnCode;
                        }
                    }
                    finally
                    {
                        RemoveFileAccessRule(parser.Profiler64Path, new64BitAccessRule);
                    }
                }
                finally
                {
                    RemoveFileAccessRule(parser.Profiler32Path, new32BitAccessRule);
                }
            }
            catch (ExitApplicationWithoutReportingException)
            {
                LogMandatoryFatal(GitHubIssuesStatement);
                LogMandatoryFatal(GitHubIssuesListUrl);
                return MakeExitCode(ProgramExitCodes.ApplicationExitDueToError);
            }
            catch (Exception ex)
            {
                LogMandatoryFatal("At: Program.Main");
                LogMandatoryFatal($"An {ex.GetType()} occured: {ex.Message}.");
                LogMandatoryFatal($"stack: {ex.StackTrace}");
                LogMandatoryFatal(GitHubIssuesStatement);
                LogMandatoryFatal(GitHubIssuesListUrl);
                return MakeExitCode(ProgramExitCodes.ApplicationExitDueToUnexpectedException);
            }
        }

        private static bool DoesUserHaveReadAndExecuteAccess(string path, string username, out FileSystemAccessRule newAccessRule)
        {
            newAccessRule = null;

            try
            {
                // Note: EffectiveAccess code can return a false negative result - it is possible that HasReadAccess 
                // may incorrectly return false. Similar behavior was witnessed when using the Windows 10 Effective
                // Permission screen to check read access for an ApplicationPoolIdentity with read access via a
                // local Windows group.

                var effectiveAccess = new EffectiveAccess.EffectiveAccess(path, username);
                if (effectiveAccess.HasReadAccess)
                {
                    return true;
                }

                Logger.Info($"Attempting to add access rule because expected owner of application under test ({username}) may not have read and execute permissions to profiler library ({path})...");

                FileSecurity accessControl;
                try
                {
                    newAccessRule = new FileSystemAccessRule(username, FileSystemRights.Read | FileSystemRights.ReadAndExecute | FileSystemRights.Synchronize, InheritanceFlags.None, PropagationFlags.None, AccessControlType.Allow);

                    accessControl = File.GetAccessControl(path);
                    accessControl.AddAccessRule(newAccessRule);
                }
                catch (Exception e)
                {
                    Logger.Error($"Unable to obtain DACL for granting read and execute permissions to profiler library ({path}) to expected owner of application under test ({username}): {e.Message}");
                    return false;
                }

                try
                {
                    File.SetAccessControl(path, accessControl);

                    // Deny rules override Allow rules, so make sure updated access control list permits read access
                    var effectiveAccessAfterDacl = new EffectiveAccess.EffectiveAccess(path, username);
                    if (effectiveAccessAfterDacl.HasReadAccess)
                    {
                        return true;
                    }

                    RemoveFileAccessRule(path, newAccessRule);
                    newAccessRule = null;
                    return false;
                }
                catch (Exception e)
                {
                    Logger.Error($"Unable to apply DACL for profiler library ({path}) to grant read permission to expected owner of application under test ({username}): {e.Message}");
                }
            }
            catch (IdentityNotMappedException e)
            {
                Logger.Error($"Unable to find expected owner of application under test. The user \"{username}\" may not exist: {e.Message}");
            }
            catch (Exception e)
            {
                Logger.Error($"Unable to determine whether username {username} has access to path {path}: {e.Message}");
            }

            return false;
        }

        private static void RemoveFileAccessRule(string path, FileSystemAccessRule rule)
        {
            if (rule == null)
            {
                return;
            }

            var accessControl = File.GetAccessControl(path);
            accessControl.RemoveAccessRule(rule);
            File.SetAccessControl(path, accessControl);
        }

        private static int RunWithContainer(CommandLineParser parser, Bootstrapper container, IPersistance persistance)
        {
            var returnCode = 0;
            var registered = false;

            try
            {
                if (parser.Register)
                {
                    Logger.Debug("Registering profiler...");
                    ProfilerRegistration.Register(parser.Registration);

                    registered = true;
                }

                var harness = container.Resolve<IProfilerManager>();

                var servicePrincipalList = new List<string>();
                if (parser.Iis)
                {
                    Logger.Debug($"Profiler configuration will use App Pool identity '{parser.IisAppPoolIdentity}'.");
                    servicePrincipalList.Add(parser.IisAppPoolIdentity);
                }

                harness.RunProcess(environment =>
                {
                    returnCode = parser.Iis ? RunIisWebApplication(parser, environment) : RunProcess(parser, environment);
                }, servicePrincipalList.ToArray());

                CalculateAndDisplayResults(persistance.CoverageSession, parser);
            }
            finally
            {
                if (registered)
                {
                    Logger.Debug("Unregistering profiler...");
                    ProfilerRegistration.Unregister(parser.Registration);
                }
            }
            return returnCode;
        }

        private static int RunIisWebApplication(CommandLineParser parser, Action<StringDictionary> environment)
        {
            var iisServiceApplicationProfilingKey = $"{ServiceApplicationProfilingKey}-{WorldWideWebPublishingServiceName}";

            var mutex = new System.Threading.Mutex(true, iisServiceApplicationProfilingKey, out var result);
            if (!result)
            {
                LogMandatoryFatal("Another instance of this application is already profiling an IIS web application.");
                return MakeExitCode(ProgramExitCodes.IisWebApplicationProfilingAlreadyRunning);
            }
            GC.KeepAlive(mutex);

            using (var w3SvcService = new ServiceControl(WorldWideWebPublishingServiceName))
            using (var wasService = new ServiceControl(WindowsProcessActivationServiceName))
            {
                var services = new[] {w3SvcService, wasService};

                // svchost.exe will have two services registered in its process and will
                // continue to run, hosting the was service, when the w3svc service stops

                foreach (var serviceToStopBeforeTrace in services)
                {
                    Logger.Info($"Stopping the service named '{serviceToStopBeforeTrace.ServiceDisplayName}', if necessary");
                    if (serviceToStopBeforeTrace.StopService(parser.ServiceControlTimeout))
                    {
                        continue;
                    }
                    LogMandatoryFatal($"Service '{serviceToStopBeforeTrace.ServiceDisplayName}' failed to stop.");
                    return MakeExitCode(ProgramExitCodes.CannotStopServiceBeforeTrace);
                }

                // now to set the environment variables
                var profilerEnvironment = new StringDictionary();
                environment(profilerEnvironment);

                if (parser.DiagMode)
                {
                    profilerEnvironment[@"OpenCover_Profiler_Diagnostics"] = "true";
                }

                try
                {
                    Logger.Info($"Starting service '{w3SvcService.ServiceDisplayName}'.");

                    w3SvcService.StartServiceWithPrincipalBasedEnvironment(parser.ServiceControlTimeout, profilerEnvironment);
                    Logger.Info($"Service started '{w3SvcService.ServiceDisplayName}'.");
                }
                catch (InvalidOperationException fault)
                {
                    LogMandatoryFatal($"Service launch failed with '{fault}'");
                    return MakeExitCode(ProgramExitCodes.ServiceFailedToStart);
                }

                Logger.Info("Trace started successfully");
                LogMandatoryInfo($"Trace will stop when either '{WorldWideWebPublishingServiceDisplayName}' stops or Code Pulse ends the trace.");

                var service = w3SvcService;
                Task.Run(() =>
                {
                    System.Console.WriteLine();
                    System.Console.WriteLine();
                    System.Console.WriteLine("Press Enter to end web application and tracing...");
                    System.Console.ReadLine();

                    Logger.Info("Stopping service...");
                    service.StopService(TimeSpan.MaxValue);
                });

                Task.WaitAny(
                    Task.Run(() => service.WaitForStatus(ServiceControllerStatus.Stopped)),
                    Task.Run(() => _persistence.WaitForShutdown()));

                foreach (var serviceToStopAfterTrace in services)
                {
                    Logger.Info($"Stopping '{serviceToStopAfterTrace.ServiceDisplayName}'.");
                    if (serviceToStopAfterTrace.StopService(parser.ServiceControlTimeout))
                    {
                        continue;
                    }
                    LogMandatoryFatal($"Service '{serviceToStopAfterTrace.ServiceDisplayName}' failed to stop after trace ended.");
                    return MakeExitCode(ProgramExitCodes.CannotStopServiceAfterTrace);
                }

                if (w3SvcService.InitiallyStarted && !w3SvcService.StartService(parser.ServiceControlTimeout))
                {
                    LogMandatoryFatal($"Unable to restart service named  '{WorldWideWebPublishingServiceDisplayName}'.");
                    return MakeExitCode(ProgramExitCodes.CannotRestoreServiceStatus);
                }

                Logger.Info("IIS web application trace completed.");

                return MakeExitCode(ProgramExitCodes.Success);
            }
        }

        private static int RunProcess(CommandLineParser parser, Action<StringDictionary> environment)
        {
            var targetPathname = ResolveTargetPathname(parser);

            Logger.Info($"Executing: {Path.GetFullPath(targetPathname)}...");

            var startInfo = new ProcessStartInfo(targetPathname);
            environment(startInfo.EnvironmentVariables);

            if (parser.DiagMode)
            {
                startInfo.EnvironmentVariables[@"OpenCover_Profiler_Diagnostics"] = "true";
            }

            startInfo.Arguments = parser.TargetArgs;
            startInfo.UseShellExecute = false;
            startInfo.WorkingDirectory = parser.TargetDir;

            try
            {
                var process = Process.Start(startInfo);
                if (process == null)
                {
                    LogMandatoryFatal("Process unexpectedly did not start");
                    return ProgramExitCodes.RunProcessFailed;
                }

                Logger.Info("Trace started successfully");
                LogMandatoryInfo("Trace will stop when either program ends or Code Pulse ends the trace.");

                Task.WaitAny(
                    Task.Run(() => process.WaitForExit()),
                    Task.Run(() => _persistence.WaitForShutdown()));

                if (!process.HasExited)
                {
                    LogMandatoryInfo("Trace ended...waiting for program exit");
                    process.WaitForExit();
                }

                var exitCode = parser.ReturnTargetCode ? process.ExitCode : 0;
                Logger.Info($"Application trace completed. Reported exited code is {exitCode}.");

                return exitCode;
            }
            catch (Exception)
            {
                LogMandatoryFatal($"Failed to execute the following command '{startInfo.FileName} {startInfo.Arguments}'.");
                return MakeExitCode(ProgramExitCodes.RunProcessFailed);
            }
        }

        private static IEnumerable<string> GetSearchPaths(string targetDir)
        {
            return (new[] { Environment.CurrentDirectory, targetDir }).Concat((Environment.GetEnvironmentVariable("PATH") ?? Environment.CurrentDirectory).Split(Path.PathSeparator));
        }

        private static string ResolveTargetPathname(CommandLineParser parser)
        {
            var expandedTargetName = Environment.ExpandEnvironmentVariables(parser.Target);
            var expandedTargetDir = Environment.ExpandEnvironmentVariables(parser.TargetDir ?? string.Empty);
            return Path.IsPathRooted(expandedTargetName) ? Path.Combine(Environment.CurrentDirectory, expandedTargetName) :
                GetSearchPaths(expandedTargetDir).Select(dir => Path.Combine(dir.Trim('"'), expandedTargetName)).FirstOrDefault(File.Exists) ?? expandedTargetName;
        }

        private static IFilter BuildFilter(CommandLineParser parser)
        {
            var filter = Filter.BuildFilter(parser);
            if (!string.IsNullOrWhiteSpace(parser.FilterFile))
            {
                if (!File.Exists(parser.FilterFile.Trim()))
                    LogMandatoryWarning(string.Format("FilterFile '{0}' cannot be found - have you specified your arguments correctly?", parser.FilterFile));
                else
                {
                    var filters = File.ReadAllLines(parser.FilterFile);
                    filters.ToList().ForEach(filter.AddFilter);
                }
            }
            else
            {
                if (parser.Filters.Count == 0)
                    filter.AddFilter("+[*]*");
            }

            return filter;
        }

        private static IPerfCounters CreatePerformanceCounter(CommandLineParser parser)
        {
            return parser.EnablePerformanceCounters ? (IPerfCounters) new PerfCounters() : new NullPerfCounter();
        }

        private static bool ParseCommandLine(string[] args, out CommandLineParser parser)
        {
            parser = new CommandLineParser(args);

            try
            {
                parser.ExtractAndValidateArguments();

                if (parser.PrintUsage)
                {
                    System.Console.WriteLine(parser.Usage());
                    return false;
                }

                if (parser.PrintVersion)
                {
                    var entryAssembly = System.Reflection.Assembly.GetEntryAssembly();
                    if (entryAssembly == null)
                    {
                        LogMandatoryFatal("Entry assembly is unavailable.");
                        return false;
                    }

                    var version = entryAssembly.GetName().Version;
                    System.Console.WriteLine("Code Pulse .NET Tracer version {0}", version);
                    if (args.Length == 1)
                    { 
                        return false;
                    }
                }

                if (!string.IsNullOrWhiteSpace(parser.TargetDir) && !Directory.Exists(parser.TargetDir))
                {
                    var invalidTargetDirectoryMessage = $"TargetDir '{parser.TargetDir}' cannot be found - have you specified your arguments correctly?";

                    LogMandatoryFatal(invalidTargetDirectoryMessage);
                    return false;
                }

                if (!parser.Iis && !File.Exists(ResolveTargetPathname(parser)))
                {
                    LogMandatoryFatal($"Target '{parser.Target}' cannot be found - have you specified your arguments correctly?");
                    return false;
                }
            }
            catch (Exception ex)
            {
                LogMandatoryFatal($"Incorrect Arguments: {ex.Message}");

                var executingAssemblyName = System.Reflection.Assembly.GetExecutingAssembly().GetName().Name;
                LogMandatoryFatal($"Review usage statement by running: {executingAssemblyName} -?");

                return false;
            }

            return true;
        }

        private static void CurrentDomainOnUnhandledException(object sender, UnhandledExceptionEventArgs unhandledExceptionEventArgs)
        {
            var ex = (Exception)unhandledExceptionEventArgs.ExceptionObject;
            var unhandledExceptionMessage = $"An {ex.GetType()} occured: {ex.Message}";

            LogMandatoryFatal("At: CurrentDomainOnUnhandledException");
            LogMandatoryFatal(unhandledExceptionMessage);
            LogMandatoryFatal($"stack: {ex.StackTrace}");
            LogMandatoryFatal(unhandledExceptionMessage);

            Environment.Exit(MakeExitCode(ProgramExitCodes.ApplicationExitDueToUnhandledException));
        }

        private static int MakeExitCode(int exitCode)
        {
            return _returnCodeOffset + exitCode;
        }

        private static void LogMandatoryInfo(string message)
        {
            if (Logger.IsInfoEnabled)
            {
                Logger.Info(message);
                return;
            }
            System.Console.Out.WriteLine(message);
        }

        private static void LogMandatoryWarning(string message)
        {
            if (Logger.IsWarnEnabled)
            {
                Logger.Warn(message);
                return;
            }
            System.Console.Out.WriteLine(message);
        }

        private static void LogMandatoryFatal(string message)
        {
            if (Logger.IsFatalEnabled)
            {
                Logger.Fatal(message);
                return;
            }
            System.Console.Error.WriteLine(message);
        }

        #region PrintResults
        private class Results
        {
            public int AltTotalClasses;
            public int AltVisitedClasses;
            public int AltTotalMethods;
            public int AltVisitedMethods;
            public readonly List<string> UnvisitedClasses = new List<string>();
            public readonly List<string> UnvisitedMethods = new List<string>();
        }

        private static void CalculateAndDisplayResults(CoverageSession coverageSession, ICommandLine parser)
        {
            if (!Logger.IsInfoEnabled)
                return;

            var results = new Results();

            if (coverageSession.Modules != null)
            {
                CalculateResults(coverageSession, results);
            }

            DisplayResults(coverageSession, parser, results);
        }

        private static void CalculateResults(CoverageSession coverageSession, Results results)
        {
            foreach (var @class in
                                from module in coverageSession.Modules.Where(x => x.Classes != null)
                                from @class in module.Classes.Where(c => !c.ShouldSerializeSkippedDueTo())
                                select @class)
            {
                if (@class.Methods == null)
                    continue;

                if (!@class.Methods.Any(x => !x.ShouldSerializeSkippedDueTo() && x.SequencePoints.Any(y => y.VisitCount > 0))
                    && @class.Methods.Any(x => x.FileRef != null))
                {
                    results.UnvisitedClasses.Add(@class.FullName);
                }

                if (@class.Methods.Any(x => x.Visited))
                {
                    results.AltVisitedClasses += 1;
                    results.AltTotalClasses += 1;
                }
                else if (@class.Methods.Any())
                {
                    results.AltTotalClasses += 1;
                }

                foreach (var method in @class.Methods.Where(x => !x.ShouldSerializeSkippedDueTo()))
                {
                    if (method.FileRef != null && !method.SequencePoints.Any(x => x.VisitCount > 0))
                        results.UnvisitedMethods.Add(method.FullName);

                    results.AltTotalMethods += 1;
                    if (method.Visited)
                    {
                        results.AltVisitedMethods += 1;
                    }
                }
            }
        }

        private static void DisplayResults(CoverageSession coverageSession, ICommandLine parser, Results results)
        {
            if (coverageSession.Summary.NumClasses > 0)
            {
                Logger.InfoFormat("Visited Classes {0} of {1} ({2})", coverageSession.Summary.VisitedClasses,
                                  coverageSession.Summary.NumClasses, Math.Round(coverageSession.Summary.VisitedClasses * 100.0 / coverageSession.Summary.NumClasses, 2));
                Logger.InfoFormat("Visited Methods {0} of {1} ({2})", coverageSession.Summary.VisitedMethods,
                                  coverageSession.Summary.NumMethods, Math.Round(coverageSession.Summary.VisitedMethods * 100.0 / coverageSession.Summary.NumMethods, 2));
                Logger.InfoFormat("Visited Points {0} of {1} ({2})", coverageSession.Summary.VisitedSequencePoints,
                                  coverageSession.Summary.NumSequencePoints, coverageSession.Summary.SequenceCoverage);
                Logger.InfoFormat("Visited Branches {0} of {1} ({2})", coverageSession.Summary.VisitedBranchPoints,
                                  coverageSession.Summary.NumBranchPoints, coverageSession.Summary.BranchCoverage);

                Logger.Info("");
                Logger.Info(
                    "==== Alternative Results (includes all methods including those without corresponding source) ====");
                Logger.InfoFormat("Alternative Visited Classes {0} of {1} ({2})", results.AltVisitedClasses,
                                  results.AltTotalClasses, results.AltTotalClasses == 0 ? 0 : Math.Round(results.AltVisitedClasses * 100.0 / results.AltTotalClasses, 2));
                Logger.InfoFormat("Alternative Visited Methods {0} of {1} ({2})", results.AltVisitedMethods,
                                  results.AltTotalMethods, results.AltTotalMethods == 0 ? 0 : Math.Round(results.AltVisitedMethods * 100.0 / results.AltTotalMethods, 2));

                if (parser.ShowUnvisited)
                {
                    Logger.Info("");
                    Logger.Info("====Unvisited Classes====");
                    foreach (var unvisitedClass in results.UnvisitedClasses)
                    {
                        Logger.Info(unvisitedClass);
                    }

                    Logger.Info("");
                    Logger.Info("====Unvisited Methods====");
                    foreach (var unvisitedMethod in results.UnvisitedMethods)
                    {
                        Logger.Info(unvisitedMethod);
                    }
                }
            }
            else
            {
                Logger.Info("No results, this could be for a number of reasons. The most common reasons are:");
                Logger.Info("    1) missing PDBs for the assemblies that match the filter please review the");
                Logger.Info("    output file and refer to the Usage guide (Usage.rtf) about filters.");
                Logger.Info("    2) the profiler may not be registered correctly, please refer to the Usage");
                Logger.Info("    guide and the -register switch.");
                Logger.Info("    3) the user account for the process under test (e.g., app pool account) may");
                Logger.Info("    not have access to the registered profiler DLL.");
            }
        }
        #endregion
    }
}

