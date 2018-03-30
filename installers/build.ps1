# 
# Steps to build Code Pulse packages (requires working Code Pulse development environment)
#
# 1. git clone https://github.com/codedx/codepulse -b <branch>
# 2. change directory to .\installers
# 3. run: powershell -file .\build.ps1 [-skipInit] [-skipWindows] [-skipMac] [-skipLinux]
# 4. locate generated Code Pulse packages in this directory
#
param (
    [switch] $skipInit,
    [switch] $skipDotNetTracer,
    [switch] $skipWindows,
    [switch] $skipMac,
    [switch] $skipLinux,
    [switch] $signOutput,
    [switch] $useGitHubDotNetTracerWindowsDownloadUrl,
    [string] $version='1.0.0',
    [string] $versionForDotNetTracerWindowsDownloadUrl='1.0.0',
    [string] $releaseDate=([DateTime]::Now.ToShortDateString())
)

Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'
$VerbosePreference = 'Continue'

$dotNetTracerWindowsDownloadUrl = "https://ci.appveyor.com/api/buildjobs/VERSION_NUMBER_FOR_DOWNLOAD/artifacts/installers/CodePulse-DotNetTracer-$version-Windows.zip"
if ($useGitHubDotNetTracerWindowsDownloadUrl) {
    $dotNetTracerWindowsDownloadUrl = "https://github.com/codedx/codepulse/releases/download/vVERSION_NUMBER_FOR_DOWNLOAD/CodePulse-DotNetTracer-$version-Windows.zip"
}
$dotNetTracerWindowsDownloadUrl = $dotNetTracerWindowsDownloadUrl -replace 'VERSION_NUMBER_FOR_DOWNLOAD',$versionForDotNetTracerWindowsDownloadUrl

Write-Verbose "Building with version number $version..."

Push-Location $PSScriptRoot

. .\Scripts\common.ps1

if (-not $skipInit) {
	Write-Verbose 'Initializing...'
	.\Scripts\init.ps1
}

if (-not $skipDotNetTracer) {
    Write-Verbose 'Starting .NET Tracer build...'
    .\DotNet-Tracer\build.ps1 -signOutput:$signOutput -version $version -dotNetTracerWindowsDownloadUrl $dotNetTracerWindowsDownloadUrl
    if ($lastexitcode -ne 0) {
        Write-Verbose 'Aborting .NET Tracer build...'
        exit $lastexitcode
    }
}
	
if (-not $skipWindows) {
    Write-Verbose 'Starting Windows build...'
    .\Windows\build.ps1 -version $version -releaseDate $releaseDate -signOutput:$signOutput
    if ($lastexitcode -ne 0) {
        Write-Verbose 'Aborting Windows build...'
        exit $lastexitcode
    }
}

if (-not $skipMac) {
    Write-Verbose 'Starting macOS build...'
    .\macOS\build.ps1 -version $version -releaseDate $releaseDate -signOutput:$signOutput
    if ($lastexitcode -ne 0) {
        Write-Verbose 'Aborting macOS build...'
        exit $lastexitcode
    }
}

if (-not $skipLinux) {
    Write-Verbose 'Starting Linux build...'
    .\Linux\build.ps1 -version $version -releaseDate $releaseDate
    if ($lastexitcode -ne 0) {
        Write-Verbose 'Aborting Linux build...'
        exit $lastexitcode
    }
}

Pop-Location