#
# This script creates the Windows Code Pulse .NET Tracer package
#
param (
	[switch] $skipBuildInit,
	[switch] $skipTests
)

Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'
$VerbosePreference = 'Continue'

Push-Location $PSScriptRoot

. ..\Scripts\common.ps1

if (-not (Test-MsBuild)) {
    exit 1
}
$msbuildPath = Get-MsBuild

if (-not $skipBuildInit) {
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
}

Pop-Location; Push-Location $dotNetTracerMainPath

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

if (-not $skipTests) {
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
    write-verbose "Building OpenCover.Test.Profiler ($buildConfiguration | x86)..."
    & $msbuildPath /p:Configuration=$buildConfiguration /p:Platform=x86 /p:SolutionDir=..\ OpenCover.Test.Profiler
    if ($lastexitcode -ne 0) {
        exit $lastexitcode
    }
    write-verbose "Building OpenCover.Test.Profiler ($buildConfiguration | x64)..."
    & $msbuildPath /p:Configuration=$buildConfiguration /p:Platform=x64 /p:SolutionDir=..\ OpenCover.Test.Profiler
    if ($lastexitcode -ne 0) {
        exit $lastexitcode
    }
}

Pop-Location