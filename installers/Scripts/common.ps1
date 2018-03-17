#
# This script contains content shared by build scripts
#
Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'
$VerbosePreference = 'Continue'

Push-Location $PSScriptRoot

. .\text.ps1
. .\sbt.ps1

Pop-Location

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Invoke-CodePulsePackaging(
    [string] $codePulseVersion,
    [datetime] $codePulseReleaseDate,
    [string] $scriptRoot, 
    [string] $codePulsePath, 
    [string] $osName, 
    [string] $osRID, 
    [string] $packageCommand, 
    [string] $codePulseTargetFilename, 
    [string] $dotNetSymbolServiceRelativePath,
    [string] $symbolServiceFilename, 
    [string] $agentJarRelativePath)
{
    $filesFolderPath = join-path $scriptRoot "Files\$osName"
    if (test-path -PathType Container $filesFolderPath) {
        write-verbose "Removing Files\$osName folder..."
        Remove-Item -Path $filesFolderPath -Recurse -Force
    }

    write-verbose 'Creating Files folder structure...'
    #
    # Files
    # ├───$osName
    # │   ├───codepulse
    #     └───tracers
    #         ├───dotnet
    #         └───java
    #
    $filesFolderCodePulsePath = join-path $filesFolderPath 'codepulse'
    $filesFolderJavaTracerPath = join-path $filesFolderCodePulsePath 'tracers\java'
    $filesFolderDotNetTracerPath = join-path $filesFolderCodePulsePath 'tracers\dotnet'

    New-Item -Path $filesFolderPath -ItemType Directory | Out-Null
    New-Item -Path $filesFolderJavaTracerPath -ItemType Directory | Out-Null
    New-Item -Path $filesFolderDotNetTracerPath -ItemType Directory | Out-Null

    Push-Location $codePulsePath

    write-verbose "Editing build.sbt for version $codePulseVersion and release date $codePulseReleaseDate..."
    $buildSbtPath = join-path (get-location) 'build.sbt'
    $buildSbt = gc $buildSbtPath
    $buildSbtNew = $buildSbt | % { $_ -replace 'version\ :=\ "UNVERSIONED"',"version := `"$codePulseVersion`"" }
    $buildSbtNew = $buildSbtNew | % { $_ -replace 'BuildKeys\.releaseDate\ :=\ "N/A"',"BuildKeys.releaseDate := `"$codePulseReleaseDate`"" }
    Set-TextContent $buildSbtPath $buildSbtNew

    write-verbose "Editing application.conf for $osName packaging..."
    $applicationConfPath = join-path (get-location) 'codepulse\src\main\resources\application.conf'
    $applicationConf = gc $applicationConfPath
    $applicationNewConf = $applicationConf | % { $_ -replace 'dotnet-symbol-service/publish/','../dotnet-symbol-service/' }
    $applicationNewConf = $applicationNewConf | % { $_ -replace 'SymbolService.exe', $symbolServiceFilename }
    Set-TextContent $applicationConfPath $applicationNewConf

    $codePulsePackagePath = join-path (get-location) ".\codepulse\target\scala-2.10\$codePulseTargetFilename"
     
    if (test-path $codePulsePackagePath -PathType Leaf) {
        write-verbose "Removing outdated $codePulseTargetFilename..."
        remove-item $codePulsePackagePath -Force
    }

    write-verbose "Packaging Code Pulse ($osRID)..."
    if (-not (Invoke-Sbt $packageCommand 3 ([timespan]::FromMinutes(1)))) {
        write-verbose 'Packaging failed'
        exit 1
    }

    write-verbose "Unzipping Code Pulse package ($osName)..."
    [io.compression.zipfile]::ExtractToDirectory($codePulsePackagePath, $filesFolderPath)

    write-verbose "Restoring original '$applicationConfPath' contents..."
    Set-TextContent $applicationConfPath $applicationConf

    write-verbose 'Restoring original build.sbt contents...'
    Set-TextContent $buildSbtPath $buildSbt

    write-verbose 'Moving Java agent (Linux)...'
    move-item (join-path $filesFolderCodePulsePath $agentJarRelativePath) $filesFolderJavaTracerPath

    Pop-Location; Push-Location $dotNetSymbolServicePath

    write-verbose "Publishing .NET Symbol Service ($osName)..."
    $dotNetSymbolServiceOutputDirectory = New-Item -Path (join-path $filesFolderCodePulsePath $dotNetSymbolServiceRelativePath) -ItemType Directory
    dotnet publish -c $buildConfiguration -r $osRID -o ($dotNetSymbolServiceOutputDirectory.FullName)
    if ($lastexitcode -ne 0) {
        exit $lastexitcode
    }

    Pop-Location; Push-Location $scriptRoot

    write-verbose 'Copying .NET Tracer...'
    copy-item "..\..\dotnet-tracer\main\CodePulse.Bundle\bin\$buildConfiguration\CodePulse.DotNet.Tracer.Installer.exe" $filesFolderDotNetTracerPath

    Pop-Location
}

function Invoke-CodePulseZip(
    [string] $scriptRoot,
    [string] $osName,
    [string] $osDescription,
    [string] $codePulseVersion,
    [string] $zipFilePath,
    [string] $scriptRootRelativePath)
{
    Push-Location $scriptRoot

    write-verbose "Zipping Code Pulse package ($osName)..."
    $outputFile = join-path $scriptRoot "..\CodePulse-$codePulseVersion-$osDescription.zip"
    if (test-path $outputFile -Type Leaf) {
        write-verbose "Deleting outdated output file $outputFile"
        remove-item $outputFile -Force
    }

    & $zipFilePath $scriptRootRelativePath $outputFile
    if ($lastexitcode -ne 0) {
        exit $lastexitcode
    }

    Pop-Location
}

function Test-DotNetTracer([string] $codePulsePath, [string] $buildConfiguration) 
{
    $msiTracerBundlePath = join-path $codePulsePath "dotnet-tracer\main\CodePulse.Bundle\bin\$buildConfiguration\CodePulse.DotNet.Tracer.Installer.exe"
    Test-Path $msiTracerBundlePath -PathType Leaf
}

function Get-MsBuild()
{
    $vs2017Editions = 'Community','Professional','Enterprise'

    $vs2017Path = $null
    $vsRootFolder = 'C:\Program Files (x86)\Microsoft Visual Studio\2017'

    $vs2017Editions | % { 
        if (test-path "C:\Program Files (x86)\Microsoft Visual Studio\2017\$_") {
            $vs2017Path = "$vsRootFolder\$_"
        }
    }

    if ($vs2017Path -eq $null) {
        Write-Error "Unable to find an installed version of Visual Studio 2017 (looked for $([string]::join(', ', $vs2017Editions)) editions at '$vsRootFolder')" -ErrorAction Continue
        return $false
    }

    # NOTE: build will not work with C:\Windows\Microsoft.NET\Framework\v4.0.30319\MSBuild.exe
    "$vs2017Path\MSBuild\15.0\Bin\MSBuild.exe" 
}

function Test-MsBuild() 
{
    $msbuildPath = Get-MsBuild

    write-verbose 'Testing for msbuild.exe path...'
    if (-not (test-path $msbuildPath)) { 
        Write-Error "Expected to find msbuild.exe at $msbuildPath" -ErrorAction Continue
        return $false
    }

    write-verbose "Using msbuild.exe at $msbuildPath, version:"
    & $msbuildPath -version
    if ($lastexitcode -ne 0) {
        Write-Error "Unable to run $msbuildPath ($lastexitcode)"
        return $false
    }

    $true
}

$codePulsePath = join-path $PSScriptRoot '..\..'
$toolsPath = join-path $codePulsePath 'installers\Tools'
$zipFilePath = join-path $toolsPath 'ZipFile\bin\ZipFile.exe'
$dotNetSymbolServicePath = join-path $codePulsePath 'dotnet-symbol-service'
$dotNetTracerPath = join-path $codePulsePath 'dotnet-tracer'
$dotNetTracerMainPath = join-path $dotNetTracerPath 'main'

$buildConfiguration = 'Release'

