Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'

function Get-TextEncoding([string] $path)
{
    $leadingBytes = gc -Encoding byte -ReadCount 4 -TotalCount 4 -LiteralPath $path
    if ($leadingBytes -eq $null) {
        $leadingBytes = @()
    }

    $encoding = [Microsoft.PowerShell.Commands.FileSystemCmdletProviderEncoding]::Ascii
    if ($leadingBytes.length -ge 4 -and $leadingBytes[0] -eq 0 -and $leadingBytes[1] -eq 0 -and $leadingBytes[2] -eq 0xfe -and $leadingBytes[3] -eq 0xff) 
    {
        $encoding = [Microsoft.PowerShell.Commands.FileSystemCmdletProviderEncoding]::UTF32
    } 
    elseif ($leadingBytes.length -ge 2 -and $leadingBytes[0] -eq 0xfe -and $leadingBytes[1] -eq 0xff)
    {
        $encoding = [Microsoft.PowerShell.Commands.FileSystemCmdletProviderEncoding]::Unicode # UTF-16
    }
    elseif ($leadingBytes.length -ge 3 -and $leadingBytes[0] -eq 0xef -and $leadingBytes[1] -eq 0xbb -and $leadingBytes[2] -eq 0xbf) 
    {
        $encoding = [Microsoft.PowerShell.Commands.FileSystemCmdletProviderEncoding]::UTF8
    } 
    $encoding
}

function Test-EndsWithCrLf([string] $path) 
{
    $trailingBytes = gc -Encoding byte -Tail 2 -Path $path
    if ($trailingBytes -eq $null -or $trailingBytes.Length -ne 2) {
        return $false
    }

    $trailingBytes[0] -eq 13 -and $trailingBytes[1] -eq 10
}

function Set-TextContent([string] $path, [string[]] $text)
{
    $endsWithCrLfBefore = Test-EndsWithCrLf $path
    Set-Content -Path $path -Encoding (Get-TextEncoding $path) -Value $text 
    $endsWithCrLfAfter = Test-EndsWithCrLf $path

    if (-not $endsWithCrLfBefore -and $endsWithCrLfAfter) {
        $allBytes = gc -Encoding Byte -Path $path
        if ($allBytes.Length -lt 2) {
            return
        }
        
        $allBytes = $allBytes[0..($allBytes.Length-3)]
        [io.file]::WriteAllBytes($path, $allBytes)
    }
}

function Set-TextAt([string] $path, [int] $index, [string[]] $insertionLines) {

    $lines = gc $path

    $before = @()
    [array]::Resize([ref]$before, $index)
    [array]::Copy($lines, $before, $index)
    
    $after = @()
    [array]::Resize([ref]$after, $lines.Length - $index + 1)
    [array]::Copy($lines, $index, $after, 0, $lines.Length - $index)
    
    Set-TextContent $path ($before + $insertionLines + $after)
} 


function Test-ContentPrefix([string] $path, [string[]] $text)
{
    $lines = gc -LiteralPath $path
    if ($text.Length -ge $lines.Length) {
        return $false
    }

    for ($i = 0; $i -lt $text.Length; $i++) {
        if ($lines[$i] -ne $text[$i]) {
            return $false
        }
    }
    return $true
}

function Set-ContentPrefix([string] $path, [string[]] $prefixText)
{
    if (Test-ContentPrefix $path $prefixText) 
    {
        return
    }
    Set-TextAt $path 0 $prefixText
}
