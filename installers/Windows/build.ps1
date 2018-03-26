#
# This script creates the Windows Code Pulse package
#
param (
	[switch] $signOutput,
	[string] $version='1.0.0',
    [string] $releaseDate=([DateTime]::Now.ToShortDateString())
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

if (-not (Test-DotNetTracer $codePulsePath $buildConfiguration))
{
    Write-Error 'Code Pulse .NET Tracer does not exist. Run installers\dotnet-tracers\build.ps1 first'
    exit 1
}

write-verbose "Setting Code Pulse installer version to $version..."
$productPath = join-path (get-location) 'CodePulse.Installer.Win64\Product.wxs'
$product = gc $productPath
$productNew = $product | % { $_ -replace ([System.Text.RegularExpressions.Regex]::Escape('<?define Version = "1.0.0.0" ?>')),"<?define Version = `"$version`" ?>" }
Set-TextContent $productPath $productNew

Invoke-CodePulsePackaging `
    $version `
    $releaseDate `
    $PSScriptRoot `
    $codePulsePath `
    'Win64' `
    'win-x64' `
    'packageEmbeddedWin64' `
    "CodePulse-$($version)-win64.zip" `
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

write-verbose "Restoring original '$productPath' contents..."
Set-TextContent $productPath $product

write-verbose 'Removing extra installer file(s)...'
$outputFolder = join-path (get-location) "CodePulse.Installer.Win64\bin\$buildConfiguration"
dir $outputFolder -Exclude CodePulse.Win64.msi | % { remove-item $_.FullName -Force }

if ($signOutput) {
    $msiPath = join-path $outputFolder 'CodePulse.Win64.msi'
    $signingInstructions = @'

Use signtool.exe to sign CodePulse.Win64.msi:

"C:\Program Files (x86)\Windows Kits\10\bin\10.0.16299.0\x86\signtool.exe" sign /v /f <path-to-pfx-file> /p <pfx-file-password> /t http://timestamp.verisign.com/scripts/timstamp.dll CodePulse.Win64.msi

Press Enter *after* you have signed the bundle...

'@
    Write-Host $signingInstructions; Read-Host

    Write-Verbose 'Verifying that the MSI is signed...'
    signtool.exe verify /pa /tw $msiPath
    if ($lastexitcode -ne 0) {
        Write-Verbose 'Cannot continue because the MSI is not signed.'
        exit $lastexitcode
    }
}

Invoke-CodePulseZip `
    $PSScriptRoot `
    'CodePulse' `
    'Windows-x64' `
    $version `
    $zipFilePath `
    "CodePulse.Installer.Win64\bin\$buildConfiguration"

Pop-Location