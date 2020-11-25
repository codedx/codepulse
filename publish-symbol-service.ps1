Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'
$VerbosePreference = 'Continue'

Set-Location $PSScriptRoot

function Get-OSRID() {
    if ([environment]::Is64BitOperatingSystem -and [Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([Runtime.InteropServices.OSPlatform]::Windows)) {
        return 'win-x64'
    }
    if ([System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([Runtime.InteropServices.OSPlatform]::OSX)) {
        return 'osx-x64'
    }
    if ([environment]::Is64BitOperatingSystem -and [Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([Runtime.InteropServices.OSPlatform]::Linux)) {
        return 'linux-x64'
    }
}

function Get-SavedCommitHash() {
    if (-not (test-path dotnet-symbol-service-last-commit -pathtype leaf)) {
        return [string]::Empty
    }
    Get-Content -Path dotnet-symbol-service-last-commit | select-object -first 1
}

function Set-SavedCommitHash([string] $commitHash) {
    Set-Content -Path dotnet-symbol-service-last-commit -Value $commitHash
}

function Get-LastCommit([string] $sinceCommitHash) {
    if ($sinceCommitHash -eq [string]::Empty) {
        $log = git log --oneline dotnet-symbol-service | select-object -first 1
    } else {
        $log = git log "$sinceCommitHash..HEAD" --oneline dotnet-symbol-service | select-object -first 1
    }
    
    if ($log -eq $null) {
        return $null
    }
    $log.substring(0, $log.indexof(' '))
}

function Invoke-Publish() {
    push-location 'dotnet-symbol-service'
    dotnet publish -c Release -r (Get-OSRID) -o .\publish
    if ($LASTEXITCODE -ne 0) {
        write-error "Failed to publish with exit code $LASTEXITCODE"
        exit $LASTEXITCODE
    }
    pop-location
}

$savedCommitHash = Get-SavedCommitHash
$lastCommitHash = Get-LastCommit $savedCommitHash
if ($lastCommitHash -eq $null) {
    exit 0
}

Invoke-Publish
Set-SavedCommitHash $lastCommitHash

exit 0
