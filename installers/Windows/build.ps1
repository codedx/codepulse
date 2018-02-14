Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'
$VerbosePreference = 'Continue'

# NOTE: build will not work with C:\Windows\Microsoft.NET\Framework\v4.0.30319\MSBuild.exe
$msbuildPath = 'C:\Program Files (x86)\Microsoft Visual Studio\2017\Enterprise\MSBuild\15.0\Bin\MSBuild.exe'

write-verbose 'Testing for msbuild.exe path...'
if (-not (test-path $msbuildPath)) { 
    Write-Error "Expected to find msbuild.exe at $msbuildPath"
    exit 1
}

write-verbose 'Adding type for unzip support...'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Invoke-Sbt([string] $packageName) {
    try {
        sbt $packageName
    }
    catch {
        if ($_.Exception.Message -ne 'Java HotSpot(TM) 64-Bit Server VM warning: ignoring option MaxPermSize=256m; support was removed in 8.0') {
            exit $lastexitcode
        }
    }
}

$codePulseVersion = '2.0.0'
$buildConfiguration = 'Release'

$filesFolderPath = join-path $PSScriptRoot 'Files'
if (test-path -PathType Container $filesFolderPath) {
    write-verbose 'Removing Files folder...'
    Remove-Item -Path $filesFolderPath -Recurse -Force
}

write-verbose 'Creating Files folder...'
New-Item -Path $filesFolderPath -ItemType Directory

$filesFolderWin32Path = join-path $filesFolderPath 'Win32'
$filesFolderWin32CodePulsePath = join-path $filesFolderWin32Path 'codepulse'
$filesFolderWin32DotNetSymbolServicePath = join-path $filesFolderWin32Path 'dotnet-symbol-service'
$filesFolderWin32DotNetTracerPath = join-path $filesFolderWin32Path 'dotnet-tracer'

$filesFolderWin64Path = join-path $filesFolderPath 'Win64'
$filesFolderWin64CodePulsePath = join-path $filesFolderWin64Path 'codepulse'
$filesFolderWin64DotNetSymbolServicePath = join-path $filesFolderWin64Path 'dotnet-symbol-service'
$filesFolderWin64DotNetTracerPath = join-path $filesFolderWin64Path 'dotnet-tracer'

write-verbose 'Creating Files folder structure...'
#
# Files
# ├───Win32
# │   ├───codepulse
# │   ├───dotnet-symbol-service
# │   └───dotnet-tracer
# └───Win64
#     ├───codepulse
#     ├───dotnet-symbol-service
#     └───dotnet-tracer
#
New-Item -Path $filesFolderWin32Path -ItemType Directory
New-Item -Path $filesFolderWin32CodePulsePath -ItemType Directory
New-Item -Path $filesFolderWin32DotNetSymbolServicePath -ItemType Directory
New-Item -Path $filesFolderWin32DotNetTracerPath -ItemType Directory
New-Item -Path $filesFolderWin64Path -ItemType Directory
New-Item -Path $filesFolderWin64CodePulsePath -ItemType Directory
New-Item -Path $filesFolderWin64DotNetSymbolServicePath -ItemType Directory
New-Item -Path $filesFolderWin64DotNetTracerPath -ItemType Directory

$codePulsePath = join-path $PSScriptRoot '..\..'
$dotNetSymbolServicePath = join-path $codePulsePath 'dotnet-symbol-service'
$dotNetTracerPath = join-path $codePulsePath 'dotnet-tracer'
$dotNetTracerMainPath = join-path $dotNetTracerPath 'main'

Push-Location $codePulsePath

write-verbose 'Packaging Code Pulse (Win32)...'
Invoke-Sbt packageEmbeddedWin32

write-verbose 'Unzipping Code Pulse package (Win32)...'
$win32CodePulsePackagePath = join-path (get-location) ".\codepulse\target\scala-2.10\CodePulse-$($codePulseVersion)-SNAPSHOT-win32.zip"
[io.compression.zipfile]::ExtractToDirectory($win32CodePulsePackagePath, $filesFolderWin32CodePulsePath)

write-verbose 'Packaging Code Pulse (Win64)...'
Invoke-Sbt packageEmbeddedWin64

write-verbose 'Unzipping Code Pulse package (Win64)...'
$win64CodePulsePackagePath = join-path (get-location) ".\codepulse\target\scala-2.10\CodePulse-$($codePulseVersion)-SNAPSHOT-win64.zip"
[io.compression.zipfile]::ExtractToDirectory($win64CodePulsePackagePath, $filesFolderWin64CodePulsePath)

Pop-Location; Push-Location $dotNetSymbolServicePath

write-verbose 'Publishing .NET Symbol Service (Win32)...'
dotnet publish -c $buildConfiguration -r win10-x86 -o $filesFolderWin32DotNetSymbolServicePath
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

write-verbose 'Publishing .NET Symbol Service (Win64)...'
dotnet publish -c $buildConfiguration -r win10-x64 -o $filesFolderWin64DotNetSymbolServicePath
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

Pop-Location; Push-Location $dotNetTracerPath

write-verbose 'Establishing .snk file...'
.\build.bat create-snk
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

write-verbose 'Establishing version files...'
.\build.bat get-version-number
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

Pop-Location; Push-Location $dotNetTracerMainPath

write-verbose 'Reporting msbuild version...'
write-host 'Using msbuild version:' 
& $msbuildPath -version
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

write-verbose "Restoring NuGet packages..."
.\.nuget\nuget.exe Restore
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

$installerConfigurationName = "$buildConfiguration-Installer"

write-verbose "Building .NET Tracer ($installerConfigurationName | x86)..."
& $msbuildPath /p:Configuration=$installerConfigurationName /p:Platform=x86 OpenCover.sln
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

write-verbose "Building .NET Tracer installer ($buildConfiguration | x86)..."
& $msbuildPath /p:Configuration=$buildConfiguration /p:Platform=x86 CodePulse.Installer
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

$msiTracerWin32Path = join-path (get-location) ".\CodePulse.Installer\bin\$buildConfiguration\CodePulse.DotNet.Tracer.msi"
copy-item $msiTracerWin32Path $filesFolderWin32DotNetTracerPath

write-verbose "Building .NET Tracer ($installerConfigurationName | x64)..."
& $msbuildPath /p:Configuration=$installerConfigurationName /p:Platform=x64 OpenCover.sln
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

write-verbose "Building .NET Tracer installer ($buildConfiguration | x64)..."
& $msbuildPath /p:Configuration=$buildConfiguration /p:Platform=x64 CodePulse.Installer.x64
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

$msiTracerWin64Path = join-path (get-location) ".\CodePulse.Installer.x64\bin\x64\$buildConfiguration\CodePulse.DotNet.Tracer.x64.msi"
copy-item $msiTracerWin64Path $filesFolderWin64DotNetTracerPath

Pop-Location