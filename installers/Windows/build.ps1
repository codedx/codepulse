#
# This script creates the Windows Code Pulse package
#
param (
	[switch] $forceTracerRebuild
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

if ($forceTracerRebuild -or (-not (Test-DotNetTracer $codePulsePath $buildConfiguration))) {
    & "$codePulsePath\installers\DotNet-Tracer\build.ps1"
}

Invoke-CodePulsePackaging `
    $codePulseVersion `
    $PSScriptRoot `
    $codePulsePath `
    'Win64' `
    'win-x64' `
    'packageEmbeddedWin64' `
    "CodePulse-$($codePulseVersion)-SNAPSHOT-win64.zip" `
    '..\dotnet-symbol-service' `
    'SymbolService.exe' `
    'agent.jar'
    
write-verbose 'Moving tracers folders to satisfy heat.ps1 requirement...'
Move-Item 'Files\Win64\codepulse\tracers' 'Files\Win64'

write-verbose 'Running heat.ps1...'
.\heat.ps1

write-verbose "Building Code Pulse installer ($buildConfiguration | x64)..."
& $msbuildPath /p:Configuration=$buildConfiguration /p:Platform=x64 Windows.sln
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

write-verbose 'Removing extra installer file(s)...'
$outputFolder = join-path (get-location) "CodePulse.Installer.Win64\bin\$buildConfiguration"
dir $outputFolder -Exclude CodePulse.Win64.msi | % { remove-item $_.FullName -Force }

Invoke-CodePulseZip `
    $PSScriptRoot `
    'Windows' `
    'Windows-x64' `
    $codePulseVersion `
    $zipFilePath `
    "CodePulse.Installer.Win64\bin\$buildConfiguration"

Pop-Location