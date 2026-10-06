[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$exampleRoot = Join-Path $projectRoot 'examples/minimal-memory'
$outputRoot = Join-Path $projectRoot 'build/plugin-examples'
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$target = Join-Path $outputRoot 'eleckoi-memory-contract-test.zip'
Compress-Archive -LiteralPath (Join-Path $exampleRoot 'plugin.json'), (Join-Path $exampleRoot 'plugin.js') -DestinationPath $target -Force
Write-Output $target
