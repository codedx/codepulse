Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'
$VerbosePreference = 'Continue'

Write-Verbose 'Starting Windows build...'
.\installers\Windows\build.ps1