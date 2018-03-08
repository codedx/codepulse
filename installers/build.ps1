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
    [switch] $signOutput,
	$version='1.0.0.0'
)

Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'
$VerbosePreference = 'Continue'

Write-Verbose "Building with version number $version..."

Push-Location $PSScriptRoot

. .\Scripts\common.ps1

if (-not $skipInit) {
	Write-Verbose 'Initializing...'
	.\Scripts\init.ps1
}

Write-Verbose 'Starting .NET Tracer build...'
.\DotNet-Tracer\build.ps1 -version $version

if ($signOutput) {
    $bundlePath = join-path $dotNetTracerMainPath "CodePulse.Bundle\bin\$buildConfiguration\CodePulse.DotNet.Tracer.Installer.exe"

    $signingInstructions = @'

Use signtool.exe and insignia.exe to sign CodePulse.DotNet.Tracer.Installer.exe:

Step 1: Extract engine.exe from CodePulse.DotNet.Tracer.Installer.exe

"C:\Program Files (x86)\WiX Toolset v3.11\bin\insignia.exe" -ib CodePulse.DotNet.Tracer.Installer.exe -o engine.exe


Step 2: Sign engine.exe

"C:\Program Files (x86)\Windows Kits\10\bin\10.0.16299.0\x86\signtool.exe" sign /v /f <path-to-pfx-file> /p <pfx-file-password> /t http://timestamp.verisign.com/scripts/timstamp.dll engine.exe


Step 3: Reattach engine.exe to CodePulse.DotNet.Tracer.Installer.exe

"C:\Program Files (x86)\WiX Toolset v3.11\bin\insignia.exe" -ab engine.exe CodePulse.DotNet.Tracer.Installer.exe -o CodePulse.DotNet.Tracer.Installer.exe


Step 4: Sign CodePulse.DotNet.Tracer.Installer.exe

"C:\Program Files (x86)\Windows Kits\10\bin\10.0.16299.0\x86\signtool.exe" sign /v /f <path-to-pfx-file> /p <pfx-file-password> /t http://timestamp.verisign.com/scripts/timstamp.dll CodePulse.DotNet.Tracer.Installer.exe


Press Enter *after* you have signed the bundle...

'@
    Write-Host $signingInstructions; Read-Host

    Write-Verbose 'Verifying that the bundle is signed...'
    signtool.exe verify /pa /tw $bundlePath
    if ($lastexitcode -ne 0) {
        Write-Verbose 'Cannot continue because the bundle is not signed.'
        exit $lastexitcode
    }
}

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