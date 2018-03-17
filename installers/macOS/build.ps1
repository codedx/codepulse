#
# This script creates the macOS Code Pulse package
#
param (
    [switch] $forceTracerRebuild,
    [switch] $signOutput,
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
    'macOS' `
    'osx-x64' `
    'packageEmbeddedOsx' `
    "CodePulse-$($version)-osx.zip" `
    'Code Pulse.app\Contents\Resources\app.nw\dotnet-symbol-service' `
    'SymbolService' `
    'Code Pulse.app\Contents\Resources\app.nw\agent.jar'

if ($signOutput) {
    $signingInstructions = @'

Step 1: Move .\Files\macOS\codepulse\Code Pulse.app to a Mac

Step 2: Open Terminal and use codesign to sign 'Code Pulse.app' bundle:

  codesign --force --verbose --deep --sign "<put-developer-id-certificate-name-here>" ./Code\ Pulse.app/

Step 3: Open Security & Privacy and set "Allow apps downloaded from" to
"App Store and identified developers"

Step 4: Run the following command to confirm execute permission by
looking for: ./Code Pulse.app/:accepted

  spctl -a -t execute -vv ./Code\ Pulse.app/

Step 5: Replace .\Files\macOS\codepulse\Code Pulse.app with the signed copy

Press Enter *after* you have signed, verified, and replaced the bundle...

'@
    Write-Host $signingInstructions; Read-Host
	
	Write-Verbose 'Verifying that the bundle is signed...'
	if (-not (test-path '.\Files\macOS\codepulse\Code Pulse.app\Contents\_CodeSignature\CodeResources' -type leaf)) {
		Write-Verbose 'Cannot continue because the bundle is not signed.'
		exit $lastexitcode
	}
}

Invoke-CodePulseZip `
    $PSScriptRoot `
    'macOS' `
    'macOS-x64' `
    $version `
    $zipFilePath `
    'Files\macOS'

Pop-Location
