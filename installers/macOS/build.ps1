# This script creates the macOS Code Pulse package

Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'
$VerbosePreference = 'Continue'

Push-Location $PSScriptRoot

. ..\Scripts\common.ps1

$filesFolderPath = join-path $PSScriptRoot 'Files'
if (test-path -PathType Container $filesFolderPath) {
    write-verbose 'Removing Files folder...'
    Remove-Item -Path $filesFolderPath -Recurse -Force
}

write-verbose 'Creating Files folder structure...'
#
# Files
# ├───macOS
# │   ├───codepulse
#         └───tracers
#             ├───dotnet
#             └───java
#
$filesFoldermacOSPath = join-path $filesFolderPath 'macOS'
$filesFoldermacOSCodePulsePath = join-path $filesFoldermacOSPath 'codepulse'
$filesFoldermacOSJavaTracerPath = join-path $filesFoldermacOSPath 'codepulse\tracers\java'
$filesFoldermacOSDotNetTracerPath = join-path $filesFoldermacOSPath 'codepulse\tracers\dotnet'

New-Item -Path $filesFolderPath -ItemType Directory | Out-Null
New-Item -Path $filesFoldermacOSJavaTracerPath -ItemType Directory | Out-Null
New-Item -Path $filesFoldermacOSDotNetTracerPath -ItemType Directory | Out-Null

Pop-Location; Push-Location $codePulsePath

write-verbose "Editing application.conf for macOS packaging..."
$applicationConfPath = join-path (get-location) 'codepulse\src\main\resources\application.conf'
$applicationConf = gc $applicationConfPath
$applicationNewConf = $applicationConf | % { $_ -replace 'dotnet-symbol-service/publish/','../dotnet-symbol-service/' }
$applicationNewConf = $applicationNewConf | % { $_ -replace 'SymbolService.exe','SymbolService' }
Set-TextContent $applicationConfPath $applicationNewConf

write-verbose 'Packaging Code Pulse (Win32)...'
Invoke-Sbt packageEmbeddedOsx

write-verbose 'Unzipping Code Pulse package (osx)...'
$macOSCodePulsePackagePath = join-path (get-location) ".\codepulse\target\scala-2.10\CodePulse-$($codePulseVersion)-SNAPSHOT-osx.zip"
[io.compression.zipfile]::ExtractToDirectory($macOSCodePulsePackagePath, $filesFoldermacOSPath)

write-verbose "Restoring original '$applicationConfPath' contents..."
Set-TextContent $applicationConfPath $applicationConf

write-verbose 'Moving Java agent (Win32)...'
move-item (join-path $filesFoldermacOSCodePulsePath 'Code Pulse.app\Contents\Resources\app.nw\agent.jar') $filesFoldermacOSJavaTracerPath

Pop-Location; Push-Location $dotNetSymbolServicePath

write-verbose 'Publishing .NET Symbol Service (macOS)...'
$dotNetSymbolServiceOutputDirectory = New-Item -Path (join-path $filesFoldermacOSCodePulsePath 'Code Pulse.app\Contents\Resources\app.nw\dotnet-symbol-service') -ItemType Directory
dotnet publish -c $buildConfiguration -r osx.10.12-x64 -o ($dotNetSymbolServiceOutputDirectory.FullName)
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

Pop-Location; Push-Location $PSScriptRoot

write-verbose 'Copying .NET Tracer...'
copy-item "..\..\installers\Windows\CodePulse.Bundle.Windows\bin\$buildConfiguration\CodePulse.Windows.exe" $filesFoldermacOSDotNetTracerPath

write-verbose 'Zipping Code Pulse package (osx)...'
$outputFile = join-path $PSScriptRoot "..\CodePulse-$codePulseVersion-macOS.zip"
if (test-path $outputFile -Type Leaf) {
    write-verbose "Deleting outdated output file $outputFile"
    remove-item $outputFile -Force
}
[io.compression.zipfile]::CreateFromDirectory($filesFoldermacOSCodePulsePath, $outputFile)

Pop-Location