//
// Modified work Copyright 2017 Secure Decisions, a division of Applied Visions, Inc.
//

using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using NUnit.Framework;
using TechTalk.SpecFlow;

namespace OpenCover.Specs.Steps
{
    [Binding]
    public class DotNetCoreSteps
    {
        [Given(@"I can find the OpenCover application")]
        public void GivenICanFindTheOpenCoverApplication()
        {
            var solutionOutputFolder = GetSolutionOutputFolder();

            Assert.IsTrue(File.Exists(Path.Combine(solutionOutputFolder, "OpenCover.Console.exe")));

            ScenarioContext.Current["TargetFolder"] = solutionOutputFolder;
        }

        [Given(@"I can find the target \.net core application '(.*)'")]
        public void GivenICanFindTheTarget_NetCoreApplication(string application)
        {
            var targetPath = GetProjectOutputFolder(application);
            if (targetPath == null)
            {
                Assert.Fail($"Expected to find base directory for application name: {application}");
            }

            var targetApp = Directory.EnumerateFiles(targetPath, $"{application}.dll", SearchOption.AllDirectories).FirstOrDefault();

            Console.WriteLine($"Found target application in '{targetApp}'");

            Assert.IsTrue(File.Exists(targetApp));

            ScenarioContext.Current["TargetApp"] = targetApp;
        }

        [Given(@"I can find the target \.net core portable application '(.*)'")]
        public void GivenICanFindTheTarget_NetCorePortableApplication(string application)
        {
            var targetPath = GetProjectOutputFolder(application);
            if (targetPath == null)
            {
                Assert.Fail($"Expected to find base directory for application name: {application}");
            }

            var targetApp = Directory.EnumerateFiles(targetPath, $"{application}.dll", SearchOption.AllDirectories).FirstOrDefault();

            Console.WriteLine($"Found target application in '{targetApp}'");

            Assert.IsTrue(File.Exists(targetApp));

            ScenarioContext.Current["TargetApp"] = targetApp;
        }

        [When(@"I execute OpenCover against the target application using the switch '(.*)'")]
        public void WhenIExecuteOpenCoverAgainstTheTargetApplicationUsingTheSwitch(string additionalSwitch)
        {
            var dotnetexe = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), @"dotnet\dotnet.exe");
            var targetApp = (string)ScenarioContext.Current["TargetApp"];
            var targetFolder = (string)ScenarioContext.Current["TargetFolder"];

            VerifyProfilerPath(targetFolder);

            var outputXml = Path.Combine(Path.GetDirectoryName(targetApp) ?? ".", "results.xml");
            if (File.Exists(outputXml))
                File.Delete(outputXml);

            var info = new ProcessStartInfo
            {
                FileName = Path.Combine(targetFolder, "OpenCover.Console.exe"),
                Arguments = $"{additionalSwitch ?? ""} -register:user \"-target:{dotnetexe}\" \"-targetargs:{targetApp}\" \"-output:{outputXml}\"",
                WorkingDirectory = targetFolder,
                UseShellExecute = false,
                RedirectStandardOutput = true
            };

            //Console.WriteLine($"{info.FileName} {info.Arguments}");

            var process = Process.Start(info);
            Assert.NotNull(process);
            var console = process.StandardOutput.ReadToEnd();
            process.WaitForExit();
            
            Assert.True(File.Exists(outputXml));

            ScenarioContext.Current["OutputXml"] = outputXml;
        }

        [Then(@"I should have a results\.xml file with a coverage greater than or equal to '(.*)'%")]
        public void ThenIShouldHaveAResults_XmlFileWithACoverageGreaterThanOrEqualTo(int coveragePercentage)
        {
            var xml = File.ReadAllText((string) ScenarioContext.Current["OutputXml"]);
            var coverage = Utils.GetTotalCoverage(xml) ?? "-1";
            Assert.GreaterOrEqual(decimal.Parse(coverage), coveragePercentage);
        }

        private string GetSolutionOutputFolder()
        {
            return $@"{AppDomain.CurrentDomain.BaseDirectory}\..\..\..\{GetRelativeOutputFolder()}";
        }

        private string GetProjectOutputFolder(string applicationName)
        {
            return $@"{AppDomain.CurrentDomain.BaseDirectory}\..\..\..\{applicationName}\{GetRelativeOutputFolder()}\netcoreapp1.1";
        }

        private string GetRelativeOutputFolder()
        {
            var currentDomainBaseDirectory = AppDomain.CurrentDomain.BaseDirectory;

            var configSuffixMatch = Regex.Match(currentDomainBaseDirectory, @"(?i)\\(?<configSuffix>bin\\(debug|release))\\*$");
            if (!configSuffixMatch.Success)
            {
                throw new InvalidOperationException($@"Expected to find path ending with \bin\debug|release in '{currentDomainBaseDirectory}'");
            }

            return configSuffixMatch.Groups["configSuffix"].Value;
        }

        private static void VerifyProfilerPath(string parentFolder)
        {
            VerifyProfilerPath(parentFolder, true);
            VerifyProfilerPath(parentFolder, false);
        }

        private static void VerifyProfilerPath(string parentFolder, bool is32Bit)
        {
            var profilerPath = Path.Combine(parentFolder, is32Bit ? "x86" : "x64", "OpenCover.Profiler.dll");
            if (!File.Exists(profilerPath))
            {
                Assert.Inconclusive($"Cannot run OpenCover. Registration requires a profiler DLL at {profilerPath}.");
            }
        }
    }
}
