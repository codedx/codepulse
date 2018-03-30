Set-PSDebug -Strict
$ErrorActionPreference = 'Stop'

function Get-SbtPath() {
    try { 
	    $sbtPaths = C:\Windows\System32\where.exe sbt 2> $null 
	    $sbtPaths[0]
    } 
    catch {
        $sbtPath = join-path $($env:USERPROFILE) 'Downloads\sbt\bin\sbt'
        if (-not (test-path $sbtPath)) {
            throw 'The sbt path cannot be found.'
        }
		$sbtPath
    }
}

function Invoke-Sbt([string] $packageName, [byte] $retryCount=0, [timespan] $retryWait=([timespan]::FromMilliSeconds(0))) {

    $try = 0
    do
    {
        if ($try -ne 0) {
            Write-Verbose "Retrying (retry number $try) after waiting for $($retryWait.TotalMilliseconds) milliseconds..."
            sleep -Milliseconds $retryWait.TotalMilliseconds
        }
        $try++

        try {
            c:\windows\system32\cmd.exe /c `"$(Get-SbtPath)`" $packageName
            if ($lastexitcode -eq 0) {
                return $true
            }
        }
        catch {
            if ($_.Exception.Message -eq 'Java HotSpot(TM) 64-Bit Server VM warning: ignoring option MaxPermSize=256m; support was removed in 8.0') {
                return $true
            }
        }
    }
    while ($try -le $retryCount)

    $false
}