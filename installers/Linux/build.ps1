#
# This script creates the Linux Code Pulse package
#
param (
	[switch] $forceTracerRebuild
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
    $codePulseVersion `
    $PSScriptRoot `
    $codePulsePath `
    'Linux' `
    'linux-x64' `
    'packageEmbeddedLinuxX64' `
    "CodePulse-$($codePulseVersion)-SNAPSHOT-linux-x64.zip" `
    'dotnet-symbol-service' `
    'SymbolService' `
    'agent.jar'

Invoke-CodePulseZip `
    $PSScriptRoot `
    'Linux' `
    'Linux-x64' `
    $codePulseVersion `
    $zipFilePath `
    'Files\Linux'

Pop-Location