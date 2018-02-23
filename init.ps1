Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'
$VerbosePreference = 'Continue'

Add-Type -AssemblyName System.IO.Compression.FileSystem

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

$sbtPath = $null
try { 
	$sbtPaths = C:\Windows\System32\where.exe sbt 2> $null 
	$sbtPath = $sbtPaths[0]
} 
catch { 
    $sbtPath = Install-SBT 'v1.0.3' 'sbt-1.0.3.zip'
}

write-verbose "Using sbt at $sbtPath"