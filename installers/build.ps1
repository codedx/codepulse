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
	[switch] $skipWindows,
	[switch] $skipMac,
	[switch] $skipLinux,
	$version='1.0.0.0'
)

Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'
$VerbosePreference = 'Continue'

Write-Verbose "Building with version number $version..."

Push-Location $PSScriptRoot

if (-not $skipInit) {
	Write-Verbose 'Initializing...'
	.\Scripts\init.ps1
}

Write-Verbose 'Starting .NET Tracer build...'
.\DotNet-Tracer\build.ps1 -version $version

if (-not $skipWindows) {
	Write-Verbose 'Starting Windows build...'
	.\Windows\build.ps1 -version $version
}

if (-not $skipMac) {
	Write-Verbose 'Starting macOS build...'
	.\macOS\build.ps1
}

if (-not $skipLinux) {
	Write-Verbose 'Starting Linux build...'
	.\Linux\build.ps1
}

Pop-Location