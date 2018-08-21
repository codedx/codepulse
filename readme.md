# Code Pulse

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) [![GitHub release](https://img.shields.io/github/release/codedx/codepulse.svg)](https://github.com/codedx/codepulse/releases) [![Build status](https://ci.appveyor.com/api/projects/status/ifckp12pjgi96jxs?svg=true)](https://ci.appveyor.com/project/CodeDx/codepulse) [![CII Best Practices](https://bestpractices.coreinfrastructure.org/projects/1760/badge)](https://bestpractices.coreinfrastructure.org/projects/1760) [![Github All Releases](https://img.shields.io/github/downloads/codedx/codepulse/total.svg)](https://github.com/codedx/codepulse) [![OWASP Labs](https://img.shields.io/badge/owasp-labs%20project-f7b73c.svg)](https://www.owasp.org/index.php/OWASP_Project_Inventory#tab=Labs_Projects)

Code Pulse is a real-time code coverage tool. It works by monitoring Java or .NET Framework applications while they run, keeps track of coverage data, and shows you what's being called and when. Code Pulse currently supports Java programs up to Java 9, and .NET Framework programs for CLR versions 2 & 4.

## Layout

**agent/** Contains the Java tracer source.

**bytefrog/** Contains the [bytefrog](https://github.com/codedx/bytefrog) source upon which the Java tracer depends.

**codepulse/** Contains the web app source.

**distrib/** Contains files that are used to package up the entirety of Code Pulse into a native app, using [node-webkit](https://github.com/rogerwang/node-webkit) in place of a browser, and [jetty](http://www.eclipse.org/jetty/) to run the server. All third party dependencies are downloaded automatically within SBT at package time.

**dotnet-symbol-service/** Contains the [.NET Symbol Service](https://github.com/codedx/dotnet-symbol-service) source upon which the .NET tracer depends.

**dotnet-tracer/** Contains the .NET tracer source that is based on a custom version of [OpenCover](https://github.com/codedx/opencover).

**installers/** Contains the scripts to package the Code Pulse software for macOS, Linux, and Windows.

**project/** Contains the SBT build definition.

## Setup

1. Install .NET Core 2.
2. Install Visual Studio 2017. 
3. Install WiX Toolset v3.11.
4. Change PowerShell Execution Policy so that local, unsigned Code Pulse PowerShell scripts can run.
5. Run .\installers\build.ps1 with desired script parameter values to create packages for macOS, Linux, and Windows.
6. Open the `installers` folder and refer to the Code Pulse [User Guide](https://github.com/codedx/codepulse/wiki/user-guide). Alternatively, run SBT in the root directory and run `container:start` to start the web server on [localhost:8080](http://localhost:8080).

## License

Code Pulse is made available under the terms of the Apache License 2.0. See the LICENSE file that accompanies this distribution for the full text of the license.
