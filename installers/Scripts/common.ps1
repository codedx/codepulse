Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'
$VerbosePreference = 'Continue'

. (join-path $PSScriptRoot 'text.ps1')
. (join-path $PSScriptRoot 'sbt.ps1')

Add-Type -AssemblyName System.IO.Compression.FileSystem

$codePulseVersion = '2.0.0'
$buildConfiguration = 'Release'

$codePulsePath = join-path $PSScriptRoot '..\..'
$dotNetSymbolServicePath = join-path $codePulsePath 'dotnet-symbol-service'
$dotNetTracerPath = join-path $codePulsePath 'dotnet-tracer'
$dotNetTracerMainPath = join-path $dotNetTracerPath 'main'