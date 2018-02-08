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
using System.Collections.Generic;
using System.ComponentModel;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text;
using System.Text.RegularExpressions;
using log4net.Core;
using Microsoft.Win32;
using OpenCover.Framework;
using OpenCover.Framework.Model;
using OpenCover.Framework.Utility;

namespace CodePulse.Console
{
    /// <summary>
    /// Parse the command line arguments based on the following syntax: <br/>
    /// [-argument[:optional-value]] [-argument[:optional-value]]
    /// </summary>
    /// <summary>
    /// Parse the command line arguments and set the appropriate properties
    /// </summary>
    public class CommandLineParser : CommandLineParserBase, ICommandLine
    {
        /// <summary>
        /// the switch -register was supplied
        /// </summary>
        public bool Register { get; private set; }

        /// <summary>
        /// Port where Code Pulse application listens for trace data.
        /// </summary>
        public int CodePulsePort { get; private set; }

        /// <summary>
        /// Host running Code Pulse.
        /// </summary>
        public string CodePulseHost { get; private set; }

        /// <summary>
        /// Time in milliseconds before aborting Code Pulse connection attempt.
        /// </summary>
        public int CodePulseConnectTimeout { get; private set; }

        /// <summary>
        /// Set when we should not use thread based buffers. 
        /// May not be as performant in some circumstances but avoids data loss
        /// </summary>
        public bool SafeMode { get; }

        /// <summary>
        /// the switch -register with the user argument was supplied i.e. -register:user
        /// </summary>
        public Registration Registration { get; private set; }

        /// <summary>
        /// whether auto-implemented properties sould be skipped 
        /// </summary>
        public bool SkipAutoImplementedProperties { get; private set; }

        /// <summary>
        /// The target executable that is to be profiled
        /// </summary>
        public string Target { get; private set; }

        /// <summary>
        /// The working directory that the action is to take place
        /// </summary>
        public string TargetDir { get; private set; }

        /// <summary>
        /// Alternate locations where PDBs can be found
        /// </summary>
        public string[] SearchDirs { get; private set; }

        /// <summary>
        /// Assemblies loaded form these dirs will be excluded
        /// </summary>
        public string[] ExcludeDirs { get; private set; }

        /// <summary>
        /// The arguments that are to be passed to the Target
        /// </summary>
        public string TargetArgs { get; private set; }

        /// <summary>
        /// Requests that the user wants to see the commandline help
        /// </summary>
        public bool PrintUsage { get; private set; }

        /// <summary>
        /// If specified then the default filters should not be applied
        /// </summary>
        public bool NoDefaultFilters { get; private set; }

        /// <summary>
        /// If specified then results to be merged by matching hash 
        /// </summary>
        public bool MergeByHash { get; private set; }

        /// <summary>
        /// Show the unvisited classes/methods at the end of the coverage run
        /// </summary>
        public bool ShowUnvisited { get; private set; }

        /// <summary>
        /// Show the unvisited classes/methods at the end of the coverage run
        /// </summary>
        public bool ReturnTargetCode { get; private set; }

        /// <summary>
        /// A list of filters
        /// </summary>
        public List<string> Filters { get; private set; }

        /// <summary>
        /// A File that has additional filters, one per line.
        /// </summary>
        public string FilterFile { get; private set; }

        /// <summary>
        /// The offset for the return code - this is to help avoid collisions between return codes and the target
        /// </summary>
        public int ReturnCodeOffset { get; private set; }

        /// <summary>
        /// A list of attribute exclusion filters
        /// </summary>
        public List<string> AttributeExclusionFilters { get; private set; }

        /// <summary>
        /// A list of file exclusion filters
        /// </summary>
        public List<string> FileExclusionFilters { get; private set; }

        /// <summary>
        /// A list of test file filters
        /// </summary>
        public List<string> TestFilters { get; private set; }

        /// <summary>
        /// A list of skipped entities to hide from being ouputted
        /// </summary>
        public List<SkippedMethod> HideSkipped { get; private set; }

        /// <summary>
        /// Set the threshold i.e. max visit count reporting
        /// </summary>
        public ulong Threshold { get; private set; }

        /// <summary>
        /// activate trace by test feature
        /// </summary>
        public bool TraceByTest { get; private set; }

        /// <summary>
        /// The logging level based on log4net.Core.Level
        /// </summary>
        public Level LogLevel { get; private set; }

        /// <summary>
        /// This means we are profiling an IIS application
        /// </summary>
        public bool Iis { get; private set; }

        /// <summary>
        /// When Iis specified, this is the identity of the account running the IIS application pool.
        /// </summary>
        public string IisAppPoolIdentity { get; private set; }

        /// <summary>
        /// Gets the timeout to wait for the service to start up or stop.
        /// </summary>
        public TimeSpan ServiceControlTimeout { get; private set; }

        /// <summary>
        /// Enable the performance counters
        /// </summary>
        public bool EnablePerformanceCounters { get; private set; }

        /// <summary>
        /// Filters are to use regular expressions rather than wild cards
        /// </summary>
        public bool RegExFilters { get; private set; }

        /// <summary>
        /// Instructs the console to print its version and exit
        /// </summary>
        public bool PrintVersion { get; private set; }

        /// <summary>
        /// Sets the 'short' timeout between profiler and host (normally 10000ms)
        /// </summary>
        public int CommunicationTimeout { get; private set; }

        /// <summary>
        /// Enable diagnostics in the profiler
        /// </summary>
        public bool DiagMode { get; private set; }

        /// <summary>
        /// Enable SendVisitPoints timer interval in msec (0 means do not run timer)
        /// </summary>
        public uint SendVisitPointsTimerInterval { get; private set; }

        /// <summary>
        /// Path for the profiler that will be used to profiler 32-bit applications.
        /// </summary>
        public string Profiler32Path { get; private set; }

        /// <summary>
        /// Path for the profiler that will be used to profiler 64-bit applications.
        /// </summary>
        public string Profiler64Path { get; private set; }

        /// <summary>
        /// Owner of application under test.
        /// </summary>
        public string ExpectedOwnerOfApplicationUnderTest { get; private set; }

        /// <summary>
        /// Constructs the parser
        /// </summary>
        /// <param name="arguments">An array of command line arguments</param>
        public CommandLineParser(string[] arguments)
            : base(arguments)
        {
            Filters = new List<string>();
            AttributeExclusionFilters = new List<string>();
            FileExclusionFilters = new List<string>();
            TestFilters = new List<string>();
            LogLevel = Level.Info;
            HideSkipped = new List<SkippedMethod>();
            EnablePerformanceCounters = false;
            TraceByTest = false;
            ServiceControlTimeout = new TimeSpan(0, 0, 30);
            RegExFilters = false;
            Registration = Registration.Normal;
            PrintVersion = false;
            ExcludeDirs = new string[0];
            SafeMode = true;
            DiagMode = false;
            SendVisitPointsTimerInterval = 0;
            Iis = false;
            CodePulseHost = "127.0.0.1";
            CodePulsePort = 8765;
            CodePulseConnectTimeout = 5000;
        }

        /// <summary>
        /// Get the usage string 
        /// </summary>
        /// <returns>The usage string</returns>
        public string Usage()
        {
            var builder = new StringBuilder();
            builder.AppendLine();
            builder.AppendLine("Usage:");
            builder.AppendLine();
            builder.AppendLine("IIS Web App Profiling (run elevated):");
            builder.AppendLine("-IIS [\"]-TargetDir:<targetdir>[\"] [\"]-IISAppPoolIdentity:<domain\\username>[\"] [[-ServiceControlTimeout:<seconds>] [common-parameters]");
            builder.AppendLine();
            builder.AppendLine("App Profiling:");
            builder.AppendLine("[\"]-Target:<target application>[\"] [[\"]-TargetDir:<targetdir>[\"]] [[\"]-TargetArgs:<arguments for the target process>[\"]] -SendVisitPointsTimerInterval:<value-greater-than-zero> [common-parameters]");
            builder.AppendLine();
            builder.AppendLine("Common-Parameters:");
            builder.AppendLine("[-Register[:user|path32|path64]]");
            builder.AppendLine("[-CodePulsePort[:port]]");
            builder.AppendLine("[-CodePulseHost[:host]]");
            builder.AppendLine("[-CodePulseConnectTimeout[:milliseconds]]");
            builder.AppendLine("[-NoDefaultFilters]");
            builder.AppendLine("[-Regex]");
            builder.AppendLine("[-MergeByHash]");
            builder.AppendLine("[-ShowUnvisited]");
            builder.AppendLine("[-ReturnTargetCode[:<returncodeoffset>]]");
            builder.AppendLine("[-ExcludeByAttribute:<filter>[;<filter>][;<filter>]]");
            builder.AppendLine("[-ExcludeByFile:<filter>[;<filter>][;<filter>]]");
            builder.AppendLine("[-CoverByTest:<filter>[;<filter>][;<filter>]]");
            builder.AppendLine("[[\"]-ExcludeDirs:<excludedir>[;<excludedir>][;<excludedir>][\"]]");
            builder.AppendLine("[[\"]-SearchDirs:<additional PDB directory>[;<additional PDB directory>][;<additional PDB directory>][\"]]");
            builder.AppendLine("[[\"]-Filter:<space separated filters>[\"]]");
            builder.AppendLine("[[\"]-FilterFile:<path to file>[\"]]");
            builder.AppendLine("[-Log:[Off|Fatal|Error|Warn|Info|Debug|Verbose|All]]");
            builder.AppendLine("[-CommunicationTimeout:<integer, e.g. 10000>");
            builder.AppendLine("[-Threshold:<max count>]");
            builder.AppendLine("[-EnablePerformanceCounters]");
            builder.AppendLine("[-SkipAutoProps]");
            builder.AppendLine("[-DiagMode]");
            builder.AppendLine("-?");
            builder.AppendLine("-Version");
            builder.AppendLine("[-SendVisitPointsTimerInterval: 0 (no timer) | 1-3,600,000 (timer interval in msec)");
            var skips = string.Join("|", Enum.GetNames(typeof(SkippedMethod)).Where(x => x != "Unknown"));
            builder.AppendLine(string.Format("[-HideSkipped:{0}|All,[{0}|All]]", skips));
            builder.AppendLine();
            builder.AppendLine("Filters:");
            builder.AppendLine("    Filters are used to include and exclude assemblies and types in the");
            builder.AppendLine("    profiler coverage; see the Usage guide. If no other filters are supplied");
            builder.AppendLine("    via the -filter option then a default inclusive all filter +[*]* is");
            builder.AppendLine("    applied.");
            builder.AppendLine("Logging:");
            builder.AppendLine("    Logging is based on log4net logging levels and appenders - defaulting");
            builder.AppendLine("    to INFO log level with ColoredConsoleAppender and RollingFileAppender.");
            builder.AppendLine("Notes:");
            builder.AppendLine("    Enclose arguments in quotes \"\" when spaces are required see -targetargs.");

            return builder.ToString();
        }

        /// <summary>
        /// Extract the arguments and validate them; also validate the supplied options when simple
        /// </summary>
        public void ExtractAndValidateArguments()
        {
            ParseArguments();

            foreach (var key in ParsedArguments.Keys)
            {
                var lower = key.ToLowerInvariant();
                switch (lower)
                {
                    case "register":
                        Register = true;
                        Enum.TryParse(GetArgumentValue("register"), true, out Registration registration);
                        Registration = registration;
                        break;
                    case "codepulseport":
                        CodePulsePort = ExtractValue("codepulseport", 0, ushort.MaxValue, error => throw new InvalidOperationException($"The Code Pulse port must be a valid port number. {error}."));
                        break;
                    case "codepulsehost":
                        CodePulseHost = GetArgumentValue("codepulsehost");
                        break;
                    case "codepulseconnecttimeout":
                        CodePulseConnectTimeout = ExtractValue("codepulseconnecttimeout", 0, int.MaxValue, error => throw new InvalidOperationException($"The Code Pulse connection timeout must be a non-negative number. {error}."));
                        break;
                    case "target":
                        Target = GetArgumentValue("target");
                        break;
                    case "targetdir":
                        TargetDir = GetArgumentValue("targetdir");
                        break;
                    case "searchdirs":
                        SearchDirs = GetArgumentValue("searchdirs").Split(';');
                        break;
                    case "excludedirs":
                        ExcludeDirs =
                            GetArgumentValue("excludedirs")
                                .Split(';')
                                .Where(_ => _ != null)
                                .Select(_ => Path.GetFullPath(Path.Combine(Directory.GetCurrentDirectory(), _)))
                                .Where(Directory.Exists)
                                .Distinct()
                                .ToArray();
                        break;
                    case "targetargs":
                        TargetArgs = GetArgumentValue("targetargs");
                        break;
                    case "nodefaultfilters":
                        NoDefaultFilters = true;
                        break;
                    case "mergebyhash":
                        MergeByHash = true;
                        break;
                    case "regex":
                        RegExFilters = true;
                        break;
                    case "showunvisited":
                        ShowUnvisited = true;
                        break;
                    case "returntargetcode":
                        ReturnTargetCode = true;
                        ReturnCodeOffset = ExtractValue("returntargetcode", 0, int.MaxValue, error => throw new InvalidOperationException($"The return target code offset must be an integer. {error}."));
                        break;
                    case "communicationtimeout":
                        CommunicationTimeout = ExtractValue("communicationtimeout", 10000, 60000, error => throw new InvalidOperationException($"The communication timeout ({GetArgumentValue("communicationtimeout")}) must be an integer. {error}."));
                        break;
                    case "filter":
                        Filters = ExtractFilters(GetArgumentValue("filter"));
                        break;
                    case "filterfile":
                        FilterFile = GetArgumentValue("filterfile");
                        break;
                    case "excludebyattribute":
                        AttributeExclusionFilters = GetArgumentValue("excludebyattribute")
                            .Split(';').ToList();
                        break;
                    case "excludebyfile":
                        FileExclusionFilters = GetArgumentValue("excludebyfile")
                            .Split(';').ToList();
                        break;
                    case "hideskipped":
                        HideSkipped = ExtractSkipped(GetArgumentValue("hideskipped"));
                        break;
                    case "coverbytest":
                        TestFilters = GetArgumentValue("coverbytest")
                            .Split(';').ToList();
                        TraceByTest = TestFilters.Any();
                        break;
                    case "log":
                        var value = GetArgumentValue("log");
                        var fieldValue = typeof(Level).GetFields(BindingFlags.Static | BindingFlags.Public).FirstOrDefault(x => string.Compare(x.Name, value, true, CultureInfo.InvariantCulture) == 0);
                        if (fieldValue == null)
                        {
                            throw new InvalidOperationException($"'{value}' is an invalid value for log parameter.");
                        }
                        LogLevel = (Level)fieldValue.GetValue(typeof(Level));
                        break;
                    case "iis":
                        Iis = true;
                        break;
                    case "iisapppoolidentity":
                        IisAppPoolIdentity = GetArgumentValue("iisapppoolidentity");
                        break;
                    case "servicecontroltimeout":
                        var timeoutValue = ExtractValue("servicecontroltimeout", 5, 60, error => throw new InvalidOperationException($"The service control timeout must be a non-negative integer. {error}."));
                        ServiceControlTimeout = TimeSpan.FromSeconds(timeoutValue);
                        break;
                    case "enableperformancecounters":
                        EnablePerformanceCounters = true;
                        break;
                    case "threshold":
                        Threshold = ExtractValue<ulong>("threshold", 0, int.MaxValue, error => throw new InvalidOperationException($"The threshold must be an integer. {error}."));
                        break;
                    case "skipautoprops":
                        SkipAutoImplementedProperties = true;
                        break;
                    case "?":
                        PrintUsage = true;
                        break;
                    case "version":
                        PrintVersion = true;
                        break;
                    case "diagmode":
                        DiagMode = true;
                        break;
                    case "sendvisitpointstimerinterval":
                        SendVisitPointsTimerInterval = ExtractValue("sendvisitpointstimerinterval", 0u, 60u * 60u * 1000u, error => throw new InvalidOperationException($"The send visit points timer interval must be a non-negative integer. {error}"));
                        break;
                    default:
                        throw new InvalidOperationException($"The argument '-{key}' is not recognised");
                }
            }

            ValidateArguments();
        }

        private T ExtractValue<T>(string argumentName, T minValueInclusive, T maxValueInclusive, Action<string> onError)
        {
            var textValue = GetArgumentValue(argumentName);
            if (!string.IsNullOrEmpty(textValue))
            {
                try
                {
                    var value = (T) TypeDescriptor
                        .GetConverter(typeof(T))
                        .ConvertFromString(textValue);

                    var typeComparer = Comparer<T>.Default;
                    if (typeComparer.Compare(value, minValueInclusive) < 0 || typeComparer.Compare(value, maxValueInclusive) > 0)
                    {
                        throw new InvalidOperationException($"The argument {argumentName} must be between {minValueInclusive} and {maxValueInclusive}");
                    }

                    return value;
                }
                catch (Exception ex)
                {
                    onError(ex.Message);
                }
            }
            return default(T);
        }

        private static List<string> ExtractFilters(string rawFilters)
        {
            // starts with required +-
            // followed by optional process-filter
            // followed by required assembly-filter 
            // followed by optional class-filter, where class-filter excludes -+" and space characters
            // followed by optional space 
            // NOTE: double-quote character from test-values somehow sneaks into default filter as last character?
            const string strRegex = @"[\-\+](<.*?>)?\[.*?\][^\-\+\s\x22]*";
            const RegexOptions myRegexOptions = RegexOptions.Singleline | RegexOptions.ExplicitCapture;
            var myRegex = new Regex(strRegex, myRegexOptions);

            return (from Match myMatch in myRegex.Matches(rawFilters) where myMatch.Success select myMatch.Value.Trim()).ToList();
        }

        private static List<SkippedMethod> ExtractSkipped(string skippedArg)
        {
            var skipped = string.IsNullOrWhiteSpace(skippedArg) ? "All" : skippedArg;
            var options = skipped.Split(';');
            var list = new List<SkippedMethod>();
            foreach (var option in options)
            {
                switch (option.ToLowerInvariant())
                {
                    case "all":
                        list = Enum.GetValues(typeof(SkippedMethod)).Cast<SkippedMethod>().Where(x => x != SkippedMethod.Unknown).ToList();
                        break;
                    default:
                        if (!Enum.TryParse(option, true, out SkippedMethod result))
                        {
                            throw new InvalidOperationException($"The hideskipped option {option} is not valid");
                        }
                        list.Add(result);
                        break;
                }
            }
            return list.Distinct().ToList();
        }

        // ReSharper disable once ParameterOnlyUsedForPreconditionCheck.Local
        private void ValidateRequiredArgument(string argumentName, string argumentValue)
        {
            if (string.IsNullOrWhiteSpace(argumentValue))
            {
                throw new InvalidOperationException($"The {argumentName} argument is required.");
            }
        }

        // ReSharper disable once ParameterOnlyUsedForPreconditionCheck.Local
        private void ValidateMissingArgument(string argumentName, string argumentValue)
        {
            if (!string.IsNullOrWhiteSpace(argumentValue))
            {
                throw new InvalidOperationException($"The {argumentName} argument is not allowed.");
            }
        }

        private void ValidateIsElevated(string error)
        {
            if (!IdentityHelper.IsRunningAsWindowsAdmin())
            {
                throw new InvalidOperationException(error);
            }
        }

        private void ValidateArguments()
        {
            if (PrintUsage || PrintVersion)
                return;

            if (string.IsNullOrWhiteSpace(Target) && !Iis)
            {
                PrintUsage = true;
                return;
            }

            ValidateProfilerRegistration();

            if (Iis)
            {
                ValidateIsElevated("You must run elevated to profile an IIS application.");
                ValidateRequiredArgument("TargetDir", TargetDir);
                ValidateRequiredArgument("IISAppPoolIdentity", IisAppPoolIdentity);
                ValidateMissingArgument("Target", Target);
                ValidateMissingArgument("TargetArgs", TargetArgs);

                if (SendVisitPointsTimerInterval != 0)
                {
                    // When running an IIS web application, Code Pulse update takes place on request end
                    throw new InvalidOperationException("SendVisitPointsTimerInterval argument is incompatible with -IIS switch.");
                }
            }
            else
            {
                ValidateRequiredArgument("Target", Target);
                if (SendVisitPointsTimerInterval == 0)
                {
                    throw new InvalidOperationException("SendVisitPointsTimerInterval command line argument must be specified as a number > 0.");
                }
            }

            if (EnablePerformanceCounters)
            {
                ValidateIsElevated("You must run elevated to enable performance counters.");
            }
        }

        private void ValidateProfilerRegistration()
        {
            ExpectedOwnerOfApplicationUnderTest = Iis ? IisAppPoolIdentity : Environment.UserName;

            if (!Register)
            {
                Profiler32Path = ValidateCurrentProfilerRegistration(false);
                Profiler64Path = ValidateCurrentProfilerRegistration(true);

                return;
            }

            if (Registration == Registration.Normal)
            {
                ValidateIsElevated("You must run elevated to register the .NET profiler component.");
            }

            Profiler32Path = ValidateProfilerPath(false);
            Profiler64Path = ValidateProfilerPath(true);
        }

        private string ValidateCurrentProfilerRegistration(bool x64)
        {
            if (x64 && !Environment.Is64BitOperatingSystem)
            {
                return null;
            }

            using (var rootKey = RegistryKey.OpenBaseKey(RegistryHive.ClassesRoot, x64 ? RegistryView.Registry64 : RegistryView.Registry32))
            using (var key = rootKey.OpenSubKey(@"CLSID\{1542C21D-80C3-45E6-A56C-A9C1E4BEB7B8}\InprocServer32"))
            {
                if (key == null)
                {
                    throw new InvalidOperationException($"Profiler for {(x64 ? "x64" : "x86")} is not registered. Register the profiler with regsvr32.exe or use the -Register command line argument.");
                }

                var path = key.GetValue(null).ToString();
                if (!System.IO.File.Exists(path))
                {
                    throw new InvalidOperationException($"Profiler is registered, but registration points to a path ({path}) that does not exist.");
                }

                return path;
            }
        }

        private static string ValidateProfilerPath(bool x64)
        {
            if (x64 && !Environment.Is64BitOperatingSystem)
            {
                return null;
            }

            var assemblyDirectory = Path.GetDirectoryName(typeof(Program).Assembly.Location) ??
                                        throw new InvalidOperationException("Unable to determine the location from which the program is running.");

            var impliedProfilerPath = Path.Combine(assemblyDirectory, x64 ? "x64" : "x86", "OpenCover.Profiler.dll");
            if (!System.IO.File.Exists(impliedProfilerPath))
            {
                throw new InvalidOperationException($"Registration cannot take place because the profiler library ({impliedProfilerPath}) does not exist. {(x64 ? "Is the 64-bit profiler library installed?" : string.Empty)}");
            }

            return impliedProfilerPath;
        }
    }
}
