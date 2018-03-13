#
# This script creates the Windows Code Pulse .NET Tracer package
#
param (
	[switch] $skipBuildInit,
	[switch] $skipTests,
	$version='1.0.0.0'
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

if (-not $skipBuildInit) {
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
}

Pop-Location; Push-Location $dotNetTracerMainPath

write-verbose "Restoring NuGet packages..."
.\.nuget\nuget.exe Restore
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

write-verbose "Setting Code Pulse Console file version to $version..."
$assemblyInfoPath = join-path (get-location) 'CodePulse.Console\Properties\AssemblyInfo.cs'
$assemblyInfo = gc $assemblyInfoPath
$assemblyInfoNew = $assemblyInfo | % { $_ -replace ([System.Text.RegularExpressions.Regex]::Escape('[assembly: AssemblyFileVersion("1.0.0.0")]')),"[assembly: AssemblyFileVersion(`"$version`")]" }
Set-TextContent $assemblyInfoPath $assemblyInfoNew

$wixVersionVariable = [System.Text.RegularExpressions.Regex]::Escape('<?define Version = "1.0.0.0" ?>')

write-verbose "Setting Code Pulse Bundle version to $version..."
$bundlePath = join-path (get-location) 'CodePulse.Bundle\Bundle.wxs'
$bundle = gc $bundlePath
$bundleNew = $bundle | % { $_ -replace $wixVersionVariable,"<?define Version = `"$version`" ?>" }
Set-TextContent $bundlePath $bundleNew

write-verbose "Setting Code Pulse x86 version to $version..."
$product32Path = join-path (get-location) 'CodePulse.Installer\Product.wxs'
$product32 = gc $product32Path
$product32New = $product32 | % { $_ -replace $wixVersionVariable,"<?define Version = `"$version`" ?>" }
Set-TextContent $product32Path $product32New

write-verbose "Setting Code Pulse x64 version to $version..."
$product64Path = join-path (get-location) 'CodePulse.Installer.x64\Product.wxs'
$product64 = gc $product64Path
$product64New = $product64 | % { $_ -replace $wixVersionVariable,"<?define Version = `"$version`" ?>" }
Set-TextContent $product64Path $product64New

$installerConfigurationName = "$buildConfiguration-Installer"

write-verbose "Building .NET Tracer ($installerConfigurationName | x64)..."
& $msbuildPath /p:Configuration=$installerConfigurationName /p:Platform=x64 OpenCover.sln
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

write-verbose "Building .NET Tracer ($installerConfigurationName | x86)..."
& $msbuildPath /p:Configuration=$installerConfigurationName /p:Platform=x86 OpenCover.sln
if ($lastexitcode -ne 0) {
    exit $lastexitcode
}

if (-not $skipTests) {
    write-verbose "Building CodePulse.Client.Test ($buildConfiguration)..."
    & $msbuildPath /p:Configuration=$buildConfiguration /p:SolutionDir=..\ CodePulse.Client.Test
    if ($lastexitcode -ne 0) {
        exit $lastexitcode
    }
    write-verbose "Building CodePulse.Console.Test ($buildConfiguration)..."
    & $msbuildPath /p:Configuration=$buildConfiguration /p:SolutionDir=..\ CodePulse.Console.Test
    if ($lastexitcode -ne 0) {
        exit $lastexitcode
    }
    write-verbose "Building OpenCover.Test ($buildConfiguration)..."
    & $msbuildPath /p:Configuration=$buildConfiguration /p:SolutionDir=..\ OpenCover.Test
    if ($lastexitcode -ne 0) {
        exit $lastexitcode
    }
    write-verbose "Building OpenCover.Test.Profiler ($buildConfiguration | x86)..."
    & $msbuildPath /p:Configuration=$buildConfiguration /p:Platform=x86 /p:SolutionDir=..\ OpenCover.Test.Profiler
    if ($lastexitcode -ne 0) {
        exit $lastexitcode
    }
    write-verbose "Building OpenCover.Test.Profiler ($buildConfiguration | x64)..."
    & $msbuildPath /p:Configuration=$buildConfiguration /p:Platform=x64 /p:SolutionDir=..\ OpenCover.Test.Profiler
    if ($lastexitcode -ne 0) {
        exit $lastexitcode
    }
}

write-verbose "Restoring original '$assemblyInfoPath' contents..."
Set-TextContent $assemblyInfoPath $assemblyInfo

write-verbose "Restoring original '$bundlePath' contents..."
Set-TextContent $bundlePath $bundle

write-verbose "Restoring original '$product32Path' contents..."
Set-TextContent $product32Path $product32

write-verbose "Restoring original '$product64Path' contents..."
Set-TextContent $product64Path $product64

Pop-Location