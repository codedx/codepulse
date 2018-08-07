#
# This script creates the Linux Code Pulse package
#
param (
	[string] $version='1.0.0',
    [string] $releaseDate=([DateTime]::Now.ToShortDateString())
)

Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'
$VerbosePreference = 'Continue'

Push-Location $PSScriptRoot

. ..\Scripts\common.ps1

if (-not (Test-DotNetTracer $codePulsePath $buildConfiguration))
{
    Write-Error 'Code Pulse .NET Tracer does not exist. Run installers\dotnet-tracer\build.ps1 first'
    exit 1
}

Invoke-CodePulsePackaging `
    $version `
    $releaseDate `
    $PSScriptRoot `
    $codePulsePath `
    'Linux' `
    'linux-x64' `
    'packageEmbeddedLinuxX64' `
    "CodePulse-$($version)-linux-x64.zip" `
    'dotnet-symbol-service' `
    'SymbolService' `
    'agent.jar'

Invoke-CodePulseZip `
    $PSScriptRoot `
    'CodePulse' `
    'Linux-x64' `
    $version `
    $zipFilePath `
    'Files\Linux'

Pop-Location