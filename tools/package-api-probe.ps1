[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
& node (Join-Path $PSScriptRoot 'generate-api-probe-manifest.mjs')
if ($LASTEXITCODE -ne 0) { throw '生成 API 清单失败' }
$exampleRoot = Join-Path $projectRoot 'examples/api-probe'
$outputRoot = Join-Path $projectRoot 'build/plugin-examples'
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$target = Join-Path $outputRoot 'eleckoi-api-probe.zip'
$files = Get-ChildItem -LiteralPath $exampleRoot -File | Select-Object -ExpandProperty FullName
Compress-Archive -LiteralPath $files -DestinationPath $target -Force
Write-Output $target
