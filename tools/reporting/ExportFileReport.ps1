param ([Parameter(Mandatory=$true)][string] $pulseFilePath)

$ErrorActionPreference = 'Stop'
Set-PSDebug -Strict

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-ArchiveTextEntries([string] $archivePath, [string[]] $entryNames) {
    $entries = @()
    $archive = [System.IO.Compression.ZipFile]::Open($archivePath, 0)
    try {
        $entryNames | % {
            $entry = $archive.getentry($_)
            if ($entry -eq $null) { throw "Unable to find entry '$_' in $archivePath" }
            $entryStream = new-object System.IO.StreamReader($entry.open())
            try {
                $entries += $entryStream.readtoend() 
            } finally {
                $entryStream.close()
            }
        }
    } finally {
        $archive.dispose()
    }
    $entries
}

function Test-ExportFileVersion([string] $pulseFilePath, [string] $version) {
    (Get-ArchiveTextEntries $pulseFilePath '.manifest' | select-string "version=$version") -ne $null
}

function Write-CodeCoverage($groupingNodes, $encounters) {
    $groupingNodes | sort-object -property 'label' | % {
        Write-Host ("{0}{1} - {2} of {3} methods ({4})" -f `
            (new-object string("`t",$_.level)),`
            $_.label, `
            $_.encounters,`
            $_.nodes,`
            $_.coverage.tostring('P'))

        if ($encounters -ne $null) {
            $groupingNode = $_
            $groupingNode.methods.keys | ? { $encounters.ContainsKey($_) } | sort-object 'label' | % {
                Write-Host ("{0}-> [{2}] {1}" -f `
                    (new-object string("`t",($groupingNode.level+1))),`
                    $groupingNode.methods[$_].label,`
                    $groupingNode.methods[$_].parent.label)
            }
        }
        Write-CodeCoverage ($_.packages.keys | % { $nodes[$_] }) $encounters
    }
}

Write-Verbose 'Checking export file version compatibility with this script...'
$supportedVersions = '2','2.1'
if (-not (($supportedVersions | % { Test-ExportFileVersion $pulseFilePath $_ }) -contains $true)) {
    throw "Unable to continue after finding unsupported manifest file version in $pulseFilePath (supported versions: $([string]::Join(',', $supportedVersions)))."
}

Write-Verbose 'Reading export file data...'
$coverageData = Get-ArchiveTextEntries $pulseFilePath 'nodes.json','encounters.json'

Write-Verbose 'Indexing nodes...'
$nodes = @{}
(ConvertFrom-Json $coverageData[0]) | % { $nodes[$_.id] = $_ } 

Write-Verbose 'Indexing encounters...'
$encounters = @{}
(ConvertFrom-Json $coverageData[1]).all | % { $encounters[$_.nodeid] = $_ }

Write-Verbose 'Processing packages/groups...'
$groupingNodes = $nodes.keys | ? { $nodes[$_].kind -eq 'package' -or $nodes[$_].kind -eq 'group' } | % { $node = $nodes[$_]
    
    $groupNode = $node
    $groupNodes = @($groupNode)

    while ($groupNode.parentId -ne $null) {
        $parentNode = $nodes[$groupNode.parentId]

        Add-Member -in $parentNode 'packages' @{} -erroract SilentlyContinue
        
        $parentNode.packages[$groupNode.id] = $groupNode
        $groupNode = $parentNode
        $groupNodes += $groupNode
    }

    $level = 0; [array]::reverse($groupNodes) 
    $groupNodes | % { 
        if ((Get-Member -in $_ 'level') -eq $null) { Add-Member -in $_ 'level' 0 }
        $_.level = $level++ 
    }

    $parentNode = $null
    if ($node.parentId -ne $null) {
        $parentNode = $nodes[$node.parentId]
    }
    
    Add-Member -in $node 'nodes' 0
    Add-Member -in $node 'encounters' 0
    Add-Member -in $node 'coverage' 0
    Add-Member -in $node 'parent' $parentNode
    Add-Member -in $node 'packages' @{} -erroract SilentlyContinue
    Add-Member -in $node 'methods' @{} -pass
}

Write-Verbose 'Processing method ancestors...'
$methodNodes = $nodes.keys | ? { $nodes[$_].kind -eq 'method' } | % { $methodNode = $nodes[$_]
    $parent = $nodes[$methodNode.parentId]
    while ($parent.kind -ne 'package' -and $parent.kind -ne 'group') {
        $parent = $nodes[$parent.parentId]
    } 
    $parent.methods[$methodNode.id] = $methodNode

    Add-Member -in $methodNode 'parent' $nodes[$methodNode.parentId]
    $methodNode
}

Write-Verbose 'Processing leaf group nodes...'
$leafGroupingNodes = $groupingNodes | ? { $_.methods.Count -gt 0 }
$leafGroupingNodes | % {
    $_.encounters = ($_.methods.keys | ? { $encounters.ContainsKey($_) }).length
    $_.nodes = $_.methods.Count
}

Write-Verbose 'Counting nodes and encounters across packages...'
$maxLevel = ($groupingNodes | % { $_.level } | measure -max).Maximum
$maxLevel..0 | % {
    $level = $_ 
    $groupingNodes | ? { $_.level -eq $level } | % { 
        $groupingNode = $_
        $groupingNode.packages.keys | % { 
                $groupingNode.encounters += $nodes[$_].encounters
                $groupingNode.nodes += $nodes[$_].nodes  
        }
        $groupingNode.coverage = 0
        if ($groupingNode.nodes -gt 0) {
            $groupingNode.coverage = $groupingNode.encounters / $groupingNode.nodes
        }
    }
}

Write-Verbose 'Finding root package/group nodes...'
$topLevelGroupingNodes = $groupingNodes | ? { $_.level -eq 0 }

Write-Verbose 'Determining total method code coverage...'
$encountersCount = 0; $nodesCount = 0; $codeCoverage = 0
$topLevelGroupingNodes | % { $encountersCount = $_.encounters; $nodesCount = $_.nodes }
$codeCoverage = $(if ($nodesCount -eq 0) { 0 } else { $encountersCount / $nodesCount })

Write-Verbose 'Writing total method code coverage by package...'
Write-Host "`nTotal Method Code Coverage: " $codeCoverage.ToString('P')

Write-Verbose 'Writing method code coverage by package...'
Write-Host "`n`nMethod Code Coverage by Package:`n"
Write-CodeCoverage $topLevelGroupingNodes

Write-Verbose 'Writing method code coverage by package and method...'
Write-Host "`n`nMethod Code Coverage by Package and Method:`n"
Write-CodeCoverage $topLevelGroupingNodes $encounters