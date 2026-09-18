[CmdletBinding()]
param(
    [string]$BundlePath,
    [switch]$SkipCatalogHashCheck
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Import-Module (Join-Path $PSScriptRoot 'deepseek/DeepSeekRuntimeBuild.psm1') -Force

function Invoke-JsonRpcSmokeProcess {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$FilePath,
        [Parameter(Mandatory)] [string[]]$ArgumentList,
        [Parameter(Mandatory)] [string[]]$Requests
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FilePath
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in $ArgumentList) { [void]$startInfo.ArgumentList.Add($argument) }

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $responses = [Collections.Generic.List[string]]::new()
    $started = $false
    try {
        if (-not $process.Start()) { throw "无法启动 Runtime 烟测进程：$FilePath" }
        $started = $true
        $stderrTask = $process.StandardError.ReadToEndAsync()
        foreach ($request in $Requests) {
            $requestFrame = $request | ConvertFrom-Json
            $expectedId = [int]$requestFrame.id
            $process.StandardInput.WriteLine($request)
            $process.StandardInput.Flush()

            while ($true) {
                $lineTask = $process.StandardOutput.ReadLineAsync()
                if (-not $lineTask.Wait([TimeSpan]::FromSeconds(30))) {
                    throw "等待 DeepSeek Runtime 响应超时：id=$expectedId"
                }
                $line = $lineTask.GetAwaiter().GetResult()
                if ($null -eq $line) {
                    $process.WaitForExit()
                    $stderr = $stderrTask.GetAwaiter().GetResult().Trim()
                    throw "DeepSeek Runtime 在响应前退出：id=$expectedId exitCode=$($process.ExitCode) stderr=$stderr"
                }
                $frame = $line | ConvertFrom-Json
                if ($null -ne $frame.PSObject.Properties['id'] -and [int]$frame.id -eq $expectedId) {
                    $responses.Add($line)
                    break
                }
            }
        }

        $process.StandardInput.Close()
        if (-not $process.WaitForExit(30000)) {
            $process.Kill($true)
            throw 'DeepSeek Runtime 在 shutdown 后未退出'
        }
        $stderr = $stderrTask.GetAwaiter().GetResult()
        if (-not [string]::IsNullOrWhiteSpace($stderr)) { Write-Host $stderr.TrimEnd() }
        if ($process.ExitCode -ne 0) {
            throw "DeepSeek Runtime 烟测失败，exitCode=$($process.ExitCode)"
        }
        return $responses.ToArray()
    } finally {
        if ($started -and -not $process.HasExited) { $process.Kill($true) }
        $process.Dispose()
    }
}

$definition = Get-DeepSeekRuntimeBuildDefinition -RuntimeRoot $PSScriptRoot
$catalog = Get-DeepSeekCatalogEntry -Definition $definition
if ([string]::IsNullOrWhiteSpace($BundlePath)) {
    $BundlePath = Join-Path $PSScriptRoot "bundles/$($definition.BundleName)"
}
$resolvedBundle = [IO.Path]::GetFullPath($BundlePath)
if (-not (Test-Path -LiteralPath $resolvedBundle -PathType Leaf)) { throw "DeepSeek Runtime 不存在：$resolvedBundle" }
$length = (Get-Item -LiteralPath $resolvedBundle).Length
if ($length -le 0 -or $length -gt $catalog.ArchiveBytesLimit) { throw "DeepSeek Runtime 大小异常：$length" }
$hash = (Get-FileHash -LiteralPath $resolvedBundle -Algorithm SHA256).Hash.ToLowerInvariant()
if (-not $SkipCatalogHashCheck -and $hash -ne $catalog.Sha256) {
    throw "DeepSeek Runtime 与 Catalog SHA-256 不一致：$hash"
}

$entries = @(& tar -tzf $resolvedBundle)
if ($LASTEXITCODE -ne 0 -or $entries.Count -eq 0) { throw 'DeepSeek Runtime 不是有效的 tar.gz' }
foreach ($rawEntry in $entries) {
    $entry = ([string]$rawEntry).Replace('\', '/').TrimEnd('/')
    if ([string]::IsNullOrWhiteSpace($entry) -or $entry -in @('.', './') -or
        $entry.StartsWith('/') -or $entry -match '^[A-Za-z]:' -or
        @($entry -split '/') -contains '..') {
        throw "DeepSeek Runtime 包含不安全路径：$rawEntry"
    }
}
foreach ($required in @(
    'bin/dsh-jsonrpc-agent',
    'bin/landlock-run',
    'bin/rg',
    'licenses/landlock-run/LICENSE',
    'licenses/ripgrep/LICENSE',
    'etc/deepseek/cordis.yml',
    'etc/deepseek/eleckoi-host-tools.mjs',
    'deepseek-harness-package.json'
)) {
    if ($required -notin $entries) { throw "DeepSeek Runtime 缺少文件：$required" }
}
if (@($entries | Where-Object { $_ -like 'lib/sharp/libvips-cpp.so.*' }).Count -ne 1) {
    throw 'DeepSeek Runtime 缺少唯一的 sharp/libvips ARM64 动态库'
}

$requests = @(
    '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"cwd":"/tmp","provider":"deepseek-official","model":"deepseek-smoke","maxTokens":4096}}',
    '{"jsonrpc":"2.0","id":2,"method":"model/resolve","params":{"provider":"deepseek-official","model":"deepseek-smoke"}}',
    '{"jsonrpc":"2.0","id":3,"method":"session/set_permission","params":{"sessionId":"eleckoi-smoke","cwd":"/tmp","preset":"approve-for-me","agentPreset":"eleckoi-default"}}',
    '{"jsonrpc":"2.0","id":4,"method":"shutdown","params":{}}'
)
$responses = @()
if (-not $IsLinux) { throw 'DeepSeek Runtime 烟测只支持 Linux ARM64 GitHub Actions runner' }
$bash = Get-Command bash -ErrorAction Stop
$nativeArchitecture = (& $bash.Source -lc 'uname -m').Trim()
if ($LASTEXITCODE -ne 0 -or $nativeArchitecture -notin @('aarch64', 'arm64')) {
    throw "原生主机架构不是 ARM64：$nativeArchitecture"
}
$tempBoundary = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd(
    [IO.Path]::DirectorySeparatorChar,
    [IO.Path]::AltDirectorySeparatorChar
) + [IO.Path]::DirectorySeparatorChar
$testRoot = [IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetTempPath()) "eleckoi-dsh-smoke-$([Guid]::NewGuid().ToString('N'))"))
if (-not $testRoot.StartsWith($tempBoundary, [StringComparison]::Ordinal)) {
    throw 'Runtime 烟测目录越出系统临时目录'
}
$nativeEnvironment = [ordered]@{
    DEEPSEEK_API_KEY = 'eleckoi-smoke'
    ELECKOI_PROVIDER_KEY = 'eleckoi-loopback'
    ELECKOI_PROVIDER_BASE_URL = 'http://127.0.0.1:1/v1'
    ELECKOI_MODEL = 'deepseek-smoke'
    ELECKOI_CONTEXT_WINDOW = '262144'
    DSH_CWD = '/tmp'
    DSH_HOME = (Join-Path $testRoot 'dsh-home')
    DSH_SESSION_ROOT = (Join-Path $testRoot 'sessions')
    DSH_TELEMETRY_DISABLED = '1'
    DSH_RIPGREP_PATH = (Join-Path $testRoot 'bin/rg')
    DSH_LANDLOCK_PATH = (Join-Path $testRoot 'bin/landlock-run')
    LD_LIBRARY_PATH = (Join-Path $testRoot 'lib/sharp')
    ELECKOI_ENABLE_WORKSPACE_TOOLS = 'true'
}
$previousEnvironment = @{}
try {
    New-Item -ItemType Directory -Path $testRoot | Out-Null
    & tar -xzf $resolvedBundle -C $testRoot
    if ($LASTEXITCODE -ne 0) { throw '无法解压 Runtime 烟测包' }
    New-Item -ItemType Directory -Path $nativeEnvironment.DSH_HOME -Force | Out-Null
    $presetRoot = Join-Path $nativeEnvironment.DSH_HOME '.agent-presets/eleckoi-default'
    New-Item -ItemType Directory -Path $presetRoot -Force | Out-Null
    $utf8WithoutBom = [Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllText(
        (Join-Path $presetRoot 'agent.cordis.yml'),
        "- id: eleckoi-smoke-persona`n  name: '@deepseek-ai/dsh-persona'`n  config:`n    prefix: 'ElecKoi ARM64 smoke'`n    suffix: ''`n    includeRuntimeContext: false`n",
        $utf8WithoutBom
    )
    [IO.File]::WriteAllText(
        (Join-Path $presetRoot 'preset.yml'),
        "name: ElecKoi ARM64 smoke`ndescription: Native Runtime mount probe.`n",
        $utf8WithoutBom
    )
    foreach ($entry in $nativeEnvironment.GetEnumerator()) {
        $previousEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, 'Process')
    }
    $ripgrep = Join-Path $testRoot 'bin/rg'
    $landlock = Join-Path $testRoot 'bin/landlock-run'
    $executable = Join-Path $testRoot 'bin/dsh-jsonrpc-agent'
    $config = Join-Path $testRoot 'etc/deepseek/cordis.yml'
    $ripgrepVersion = @(& $ripgrep --version)
    if ($LASTEXITCODE -ne 0) { throw 'Runtime 内的 ripgrep 无法启动' }
    $ripgrepVersion | ForEach-Object { Write-Host $_ }
    $landlockReport = @(& $landlock --probe 2>&1)
    if ($LASTEXITCODE -ne 0 -or $landlockReport -notmatch 'landlock: (fully|partially) enforced') {
        throw "Runtime 内的 Landlock launcher 无法实施文件沙箱：$($landlockReport -join ' ')"
    }
    $landlockReport | ForEach-Object { Write-Host $_ }
    $responses = @(
        Invoke-JsonRpcSmokeProcess `
            -FilePath $executable `
            -ArgumentList @('--profile', 'sdk', '--patch', $config) `
            -Requests $requests
    )
} finally {
    foreach ($entry in $previousEnvironment.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
    if (Test-Path -LiteralPath $testRoot) {
        $testRootItem = Get-Item -LiteralPath $testRoot -Force
        if (($testRootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "拒绝清理重解析点：$testRoot"
        }
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
if ($responses.Count -ne 4) { throw "DeepSeek Runtime 返回了异常的 JSON-RPC 响应数量：$($responses.Count)" }
$frames = @($responses | ForEach-Object { $_ | ConvertFrom-Json })
$initializeFrames = @($frames | Where-Object { [int]$_.id -eq 1 })
$modelFrames = @($frames | Where-Object { [int]$_.id -eq 2 })
$permissionFrames = @($frames | Where-Object { [int]$_.id -eq 3 })
$shutdownFrames = @($frames | Where-Object { [int]$_.id -eq 4 })
if ($initializeFrames.Count -ne 1 -or $modelFrames.Count -ne 1 -or
    $permissionFrames.Count -ne 1 -or $shutdownFrames.Count -ne 1) {
    throw "DeepSeek Runtime 返回了未知的 JSON-RPC 响应：$($frames | ConvertTo-Json -Compress -Depth 8)"
}
$initialize = $initializeFrames[0]
$model = $modelFrames[0]
$permission = $permissionFrames[0]
$shutdown = $shutdownFrames[0]
if ($null -ne $initialize.PSObject.Properties['error']) {
    throw "DeepSeek Runtime initialize 失败：$($initialize.error | ConvertTo-Json -Compress -Depth 8)"
}
if ($null -eq $initialize.PSObject.Properties['result'] -or $null -eq $initialize.result -or
    $null -eq $initialize.result.PSObject.Properties['serverInfo']) {
    throw "DeepSeek Runtime initialize 响应无效：$($initialize | ConvertTo-Json -Compress -Depth 8)"
}
if ([string]$initialize.result.serverInfo.name -ne 'deepseek-harness-sdk-runtime') {
    throw 'DeepSeek Runtime 返回了未知的服务身份'
}
if ($null -ne $model.PSObject.Properties['error'] -or
    $null -eq $model.PSObject.Properties['result'] -or $null -eq $model.result -or
    $null -eq $model.result.PSObject.Properties['reasoning'] -or $null -eq $model.result.reasoning -or
    $null -eq $model.result.reasoning.PSObject.Properties['efforts']) {
    throw "DeepSeek Runtime 官方推理能力响应无效：$($model | ConvertTo-Json -Compress -Depth 8)"
}
$efforts = @($model.result.reasoning.efforts | ForEach-Object { [string]$_.id })
if (($efforts -join ',') -ne 'off,low,high,max') {
    throw "DeepSeek Runtime 官方推理能力响应无效：$($model | ConvertTo-Json -Compress -Depth 8)"
}
if ($null -ne $permission.PSObject.Properties['error'] -or
    [string]$permission.result.preset -ne 'approve-for-me') {
    throw "DeepSeek Runtime 权限模式响应无效：$($permission | ConvertTo-Json -Compress -Depth 8)"
}
if ([int]$shutdown.id -ne 4 -or $null -eq $shutdown.result) { throw 'DeepSeek Runtime shutdown 响应无效' }
Write-Host "DeepSeek Runtime ARM64 烟测通过：$hash ($length bytes)"
