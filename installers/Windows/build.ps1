# 
# Steps to build Code Pulse (requires working Code Pulse development environment)
#
# 1. git clone https://github.com/codedx/codepulse -b <branch>
# 2. change directory to codepulse\installers\Windows
# 3. run powershell -file .\build.ps1
# 4. find the installer in codepulse\installers\Windows\CodePulse.Bundle.Windows\bin\Release

Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'
$VerbosePreference = 'Continue'

Push-Location $PSScriptRoot

. .\text.ps1

# NOTE: build will not work with C:\Windows\Microsoft.NET\Framework\v4.0.30319\MSBuild.exe

$vs2017Editions = 'Community','Professional','Enterprise'

$vs2017Path = $null
$vsRootFolder = 'C:\Program Files (x86)\Microsoft Visual Studio\2017'

$vs2017Editions | % { 
    if (test-path "C:\Program Files (x86)\Microsoft Visual Studio\2017\$_") {
        $vs2017Path = "$vsRootFolder\$_"
    }
}

if ($vs2017Path -eq $null) {
    Write-Error "Unable to find an installed version of Visual Studio 2017 (looked for $([string]::join(', ', $vs2017Editions)) editions at '$vsRootFolder')" -ErrorAction Continue
    exit 1
}

$msbuildPath = "$vs2017Path\MSBuild\15.0\Bin\MSBuild.exe"

write-verbose 'Testing for msbuild.exe path...'
if (-not (test-path $msbuildPath)) { 
    Write-Error "Expected to find msbuild.exe at $msbuildPath" -ErrorAction Continue
    exit 2
}

write-verbose "Using msbuild.exe at $msbuildPath"

write-verbose 'Adding type for unzip support...'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-SbtPath() {
    try { 
	    $sbtPaths = C:\Windows\System32\where.exe sbt 2> $null 
	    $sbtPaths[0]
    } 
    catch {
        $sbtPath = join-path $($env:USERPROFILE) 'Downloads\sbt\bin\sbt'
        if (-not (test-path $sbtPath)) {
            throw 'The sbt path cannot be found.'
        }
		$sbtPath
    }
}

function Invoke-Sbt([string] $packageName) {
    try {
        c:\windows\system32\cmd.exe /c "`"$(Get-SbtPath)`" $packageName"
    }
    catch {
        if ($_.Exception.Message -ne 'Java HotSpot(TM) 64-Bit Server VM warning: ignoring option MaxPermSize=256m; support was removed in 8.0') {
            exit $lastexitcode
        }
    }
}

$codePulseVersion = '2.0.0'
$buildConfiguration = 'Release'

$filesFolderPath = join-path $PSScriptRoot 'Files'
if (test-path -PathType Container $filesFolderPath) {
    write-verbose 'Removing Files folder...'
    Remove-Item -Path $filesFolderPath -Recurse -Force
}

write-verbose 'Creating Files folder...'
New-Item -Path $filesFolderPath -ItemType Directory

$filesFolderWin32Path = join-path $filesFolderPath 'Win32'
$filesFolderWin32CodePulsePath = join-path $filesFolderWin32Path 'codepulse'
$filesFolderWin32DotNetSymbolServicePath = join-path $filesFolderWin32Path 'dotnet-symbol-service'
$filesFolderWin32DotNetTracerPath = join-path $filesFolderWin32Path 'tracers\dotnet'
$filesFolderWin32JavaTracerPath = join-path $filesFolderWin32Path 'tracers\java'

$filesFolderWin64Path = join-path $filesFolderPath 'Win64'
$filesFolderWin64CodePulsePath = join-path $filesFolderWin64Path 'codepulse'
$filesFolderWin64DotNetSymbolServicePath = join-path $filesFolderWin64Path 'dotnet-symbol-service'
$filesFolderWin64DotNetTracerPath = join-path $filesFolderWin64Path 'tracers\dotnet'
$filesFolderWin64JavaTracerPath = join-path $filesFolderWin64Path 'tracers\java'

write-verbose 'Creating Files folder structure...'
#
# Files
# ├───Win32
# │   ├───codepulse
# │   ├───dotnet-symbol-service
#     └───tracers
#         ├───dotnet
#         └───java
# └───Win64
#     ├───codepulse
#     ├───dotnet-symbol-service
#     └───tracers
#         ├───dotnet
#         └───java
#
New-Item -Path $filesFolderWin32Path -ItemType Directory
New-Item -Path $filesFolderWin32CodePulsePath -ItemType Directory
New-Item -Path $filesFolderWin32DotNetSymbolServicePath -ItemType Directory
New-Item -Path $filesFolderWin32DotNetTracerPath -ItemType Directory
New-Item -Path $filesFolderWin32JavaTracerPath -ItemType Directory
New-Item -Path $filesFolderWin64Path -ItemType Directory
New-Item -Path $filesFolderWin64CodePulsePath -ItemType Directory
New-Item -Path $filesFolderWin64DotNetSymbolServicePath -ItemType Directory
New-Item -Path $filesFolderWin64DotNetTracerPath -ItemType Directory
New-Item -Path $filesFolderWin64JavaTracerPath -ItemType Directory

$codePulsePath = join-path $PSScriptRoot '..\..'
$dotNetSymbolServicePath = join-path $codePulsePath 'dotnet-symbol-service'
$dotNetTracerPath = join-path $codePulsePath 'dotnet-tracer'
$dotNetTracerMainPath = join-path $dotNetTracerPath 'main'

Pop-Location; Push-Location $codePulsePath

write-verbose "Editing application.conf for packaging..."
$applicationConfPath = join-path (get-location) 'codepulse\src\main\resources\application.conf'
$applicationConf = gc $applicationConfPath
$applicationNewConf = $applicationConf | % { $_ -replace 'dotnet-symbol-service/publish/','../dotnet-symbol-service/' }
Set-TextContent $applicationConfPath $applicationNewConf

write-verbose 'Packaging Code Pulse (Win32)...'
Invoke-Sbt packageEmbeddedWin32

write-verbose 'Unzipping Code Pulse package (Win32)...'
$win32CodePulsePackagePath = join-path (get-location) ".\codepulse\target\scala-2.10\CodePulse-$($codePulseVersion)-SNAPSHOT-win32.zip"
[io.compression.zipfile]::ExtractToDirectory($win32CodePulsePackagePath, $filesFolderWin32CodePulsePath)

write-verbose 'Packaging Code Pulse (Win64)...'
Invoke-Sbt packageEmbeddedWin64

write-verbose 'Unzipping Code Pulse package (Win64)...'
$win64CodePulsePackagePath = join-path (get-location) ".\codepulse\target\scala-2.10\CodePulse-$($codePulseVersion)-SNAPSHOT-win64.zip"
[io.compression.zipfile]::ExtractToDirectory($win64CodePulsePackagePath, $filesFolderWin64CodePulsePath)

write-verbose "Restoring original '$applicationConfPath' contents..."
Set-TextContent $applicationConfPath $applicationConf

write-verbose 'Moving Java agent (Win32)...'
move-item (join-path $filesFolderWin32CodePulsePath 'codepulse\agent.jar') $filesFolderWin32JavaTracerPath

write-verbose 'Moving Java agent (Win64)...'
move-item (join-path $filesFolderWin64CodePulsePath 'codepulse\agent.jar') $filesFolderWin64JavaTracerPath

Pop-Location; Push-Location $dotNetSymbolServicePath

write-verbose 'Publishing .NET Symbol Service (Win32)...'
dotnet publish -c $buildConfiguration -r win7-x86 -o $filesFolderWin32DotNetSymbolServicePath
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

write-verbose 'Publishing .NET Symbol Service (Win64)...'
dotnet publish -c $buildConfiguration -r win7-x64 -o $filesFolderWin64DotNetSymbolServicePath
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

Pop-Location; Push-Location $dotNetTracerPath

write-verbose 'Establishing .snk file...'
.\build.bat create-snk
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

write-verbose 'Establishing version files...'
.\build.bat get-version-number
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

Pop-Location; Push-Location $dotNetTracerMainPath

write-verbose 'Reporting msbuild version...'
write-host 'Using msbuild version:' 
& $msbuildPath -version
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

write-verbose "Restoring NuGet packages..."
.\.nuget\nuget.exe Restore
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

$installerConfigurationName = "$buildConfiguration-Installer"

write-verbose "Building .NET Tracer ($installerConfigurationName | x64)..."
& $msbuildPath /p:Configuration=$installerConfigurationName /p:Platform=x64 OpenCover.sln
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

write-verbose "Building .NET Tracer ($installerConfigurationName | x86)..."
& $msbuildPath /p:Configuration=$installerConfigurationName /p:Platform=x86 OpenCover.sln
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

$msiTracerBundlePath = join-path (get-location) ".\CodePulse.Bundle\bin\$buildConfiguration\CodePulse.DotNet.Tracer.Bundle.exe"
copy-item $msiTracerBundlePath $filesFolderWin32DotNetTracerPath
copy-item $msiTracerBundlePath $filesFolderWin64DotNetTracerPath

write-verbose "Building CodePulse.Client.Test ($buildConfiguration)..."
& $msbuildPath /p:Configuration=$buildConfiguration /p:SolutionDir=..\ CodePulse.Client.Test
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}
write-verbose "Building CodePulse.Console.Test ($buildConfiguration)..."
& $msbuildPath /p:Configuration=$buildConfiguration /p:SolutionDir=..\ CodePulse.Console.Test
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}
write-verbose "Building OpenCover.Test ($buildConfiguration)..."
& $msbuildPath /p:Configuration=$buildConfiguration /p:SolutionDir=..\ OpenCover.Test
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}
write-verbose "Building OpenCover.Test.Profiler ($buildConfiguration)..."
& $msbuildPath /p:Configuration=$buildConfiguration /p:SolutionDir=..\ OpenCover.Test.Profiler
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

Pop-Location; Push-Location $PSScriptRoot

write-verbose 'Running heat.ps1...'
.\heat.ps1

write-verbose "Building Code Pulse installer ($buildConfiguration | x64)..."
& $msbuildPath /p:Configuration=$buildConfiguration /p:Platform=x64 Windows.sln
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

write-verbose "Building Code Pulse installer ($buildConfiguration | x86) and bundle..."
& $msbuildPath /p:Configuration=$buildConfiguration /p:Platform=x86 Windows.sln
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

write-verbose 'Zipping Code Pulse package (Windows)...'
$outputFolder = join-path (get-location) "CodePulse.Bundle.Windows\bin\$buildConfiguration"
dir $outputFolder -Exclude CodePulse.Windows.exe | % { remove-item $_.FullName -Force }

$outputFile = join-path $filesFolderPath "CodePulse-$codePulseVersion-windows.zip"
[io.compression.zipfile]::CreateFromDirectory($outputFolder, $outputFile)

Pop-Location
