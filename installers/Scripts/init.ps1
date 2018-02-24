# This script installs sbt, which is a prerequisite for the AppVeyor build

Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'
$VerbosePreference = 'Continue'

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Test-SBT {
    $sbtPath = $null
    try { 
	    $sbtPaths = where.exe sbt 2> $null
        
        $sbtPath = $sbtPaths
        if ($sbtPaths -is [array]) {
            $sbtPath = $sbtPaths[0]
        }
    } 
    catch { 
        $downloadSbtPath = join-path $($env:USERPROFILE) 'Downloads\sbt\bin\sbt'
        if (test-path $downloadSbtPath -PathType Leaf) {
            $sbtPath = $downloadSbtPath
        }
    }

    $foundSbt = $sbtPath -ne $null
    if ($foundSbt) {
        Write-Verbose "Found sbt at $sbtPath"
    } else {
        Write-Verbose "Unable to find sbt path"
    }

    $foundSbt
}

function Install-SBT([string] $sbtVersion, [string] $sbtFilename) {

    $url = "https://github.com/sbt/sbt/releases/download/$sbtVersion/$sbtFilename"

    $downloadsFolder = join-path $($env:USERPROFILE) 'Downloads'
    $sbtArchivePath = join-path $downloadsFolder $sbtFilename

    write-verbose "Downloading $url to $sbtArchivePath..."
    (new-object System.Net.WebClient).DownloadFile($url, $sbtArchivePath)

    $sbtFolder = @(join-path $downloadsFolder 'sbt')
    if (test-path $sbtFolder) {
        write-verbose "Removing folder $sbtFolder..."
        remove-item -Path $sbtFolder -Recurse -Force 
    }

    write-verbose "Extracting $sbtArchivePath to folder $downloadsFolder..."
    [System.IO.Compression.ZipFile]::ExtractToDirectory($sbtArchivePath, $downloadsFolder)

    join-path $sbtFolder 'bin\sbt'
}

if (-not (Test-SBT)) {
    Install-SBT 'v1.0.3' 'sbt-1.0.3.zip'
}