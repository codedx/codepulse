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

function Invoke-Sbt([string] $packageName) {
    try {
        c:\windows\system32\cmd.exe /c "`"$(Get-SbtPath)`" $packageName"
    }
    catch {
        if ($_.Exception.Message -ne 'Java HotSpot(TM) 64-Bit Server VM warning: ignoring option MaxPermSize=256m; support was removed in 8.0') {
            exit $lastexitcode
        }
    }
}