#
# This script creates the Linux Code Pulse package
#
param (
	[switch] $forceTracerRebuild,
	$version='1.0.0.0',
    $releaseDate=([DateTime]::Now.ToShortDateString())
)

Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'
$VerbosePreference = 'Continue'

Push-Location $PSScriptRoot

. ..\Scripts\common.ps1

if ($forceTracerRebuild -or (-not (Test-DotNetTracer $codePulsePath $buildConfiguration))) 
{
    if (-not (Test-MsBuild)) {
        exit 1
    }

    & "$codePulsePath\installers\DotNet-Tracer\build.ps1"
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
    'Linux' `
    'Linux-x64' `
    $version `
    $zipFilePath `
    'Files\Linux'

Pop-Location