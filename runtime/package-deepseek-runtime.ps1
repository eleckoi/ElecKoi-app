[CmdletBinding()]
param(
    [string]$ArtifactDirectory,
    [string]$BaseBundle,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'bundles'),
    [switch]$ForceRepackage
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Import-Module (Join-Path $PSScriptRoot 'deepseek/DeepSeekRuntimeBuild.psm1') -Force
$definition = Get-DeepSeekRuntimeBuildDefinition -RuntimeRoot $PSScriptRoot
$resolvedOutput = [IO.Path]::GetFullPath($OutputDirectory)
if ([string]::IsNullOrWhiteSpace($ArtifactDirectory)) {
    $ArtifactDirectory = Join-Path $PSScriptRoot "artifacts/$($definition.ArtifactName)"
}
$resolvedArtifact = [IO.Path]::GetFullPath($ArtifactDirectory)
$artifactManifest = Join-Path $resolvedArtifact 'deepseek-harness-build.json'
$artifactExecutable = Join-Path $resolvedArtifact 'bin/dsh-jsonrpc-agent'
$artifactLandlock = Join-Path $resolvedArtifact 'bin/landlock-run'
$artifactLandlockLicense = Join-Path $resolvedArtifact 'licenses/landlock-run/LICENSE'
$artifactRipgrep = Join-Path $resolvedArtifact 'bin/rg'
$artifactRipgrepLicense = Join-Path $resolvedArtifact 'licenses/ripgrep/LICENSE'
$artifactSharpLibraries = Join-Path $resolvedArtifact 'lib/sharp'
$artifactSharpLicense = Join-Path $resolvedArtifact 'licenses/sharp-libvips/LGPL-3.0.txt'
$sourceMode = ''
$resolvedBaseBundle = ''
$landlockBinarySha256 = ''

function Assert-SafeArchive {
    param([Parameter(Mandatory)][string]$Path)

    $entries = @(& tar -tzf $Path)
    if ($LASTEXITCODE -ne 0 -or $entries.Count -eq 0) { throw "运行时包不是有效的 tar.gz：$Path" }
    foreach ($rawEntry in $entries) {
        $entry = ([string]$rawEntry).Replace('\', '/').TrimEnd('/')
        if ([string]::IsNullOrWhiteSpace($entry) -or $entry -in @('.', './') -or
            $entry.StartsWith('/') -or $entry -match '^[A-Za-z]:' -or
            @($entry -split '/') -contains '..') {
            throw "运行时包包含不安全路径：$rawEntry"
        }
    }
}

if ((Test-Path -LiteralPath $artifactManifest -PathType Leaf) -and
    (Test-Path -LiteralPath $artifactExecutable -PathType Leaf) -and
    (Test-Path -LiteralPath $artifactLandlock -PathType Leaf) -and
    (Test-Path -LiteralPath $artifactLandlockLicense -PathType Leaf) -and
    (Test-Path -LiteralPath $artifactRipgrep -PathType Leaf) -and
    (Test-Path -LiteralPath $artifactRipgrepLicense -PathType Leaf) -and
    (Test-Path -LiteralPath $artifactSharpLibraries -PathType Container) -and
    (@(Get-ChildItem -LiteralPath $artifactSharpLibraries -File -Filter 'libvips-cpp.so.*').Count -eq 1) -and
    (Test-Path -LiteralPath $artifactSharpLicense -PathType Leaf)) {
    $artifact = Get-Content -Raw -LiteralPath $artifactManifest | ConvertFrom-Json
    if ([string]$artifact.sourceCommit -ne $definition.SourceCommit -or
        [string]$artifact.sourcePatchSha256 -ne $definition.SourcePatchSha256 -or
        [string]$artifact.version -ne $definition.HarnessVersion -or
        [string]$artifact.buildNodeVersion -ne $definition.BuildNodeVersion -or
        [string]$artifact.packagedNodeVersion -ne $definition.PackagedNodeVersion -or
        [string]$artifact.pnpmVersion -ne $definition.PnpmVersion -or
        [string]$artifact.pkgVersion -ne $definition.PkgVersion -or
        [string]$artifact.ripgrepPackageVersion -ne $definition.RipgrepPackageVersion -or
        [string]$artifact.ripgrepBinarySha256 -ne $definition.RipgrepBinarySha256) {
        throw 'DeepSeek 编译产物与构建清单不一致'
    }
    if ((Get-Item -LiteralPath $artifactExecutable).Length -le 100MB) {
        throw 'DeepSeek 编译产物大小异常'
    }
    $actualRipgrepHash = (Get-FileHash -LiteralPath $artifactRipgrep -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualRipgrepHash -ne $definition.RipgrepBinarySha256) {
        throw 'DeepSeek 编译产物中的 ripgrep 哈希不匹配'
    }
    $landlockBinarySha256 = (Get-FileHash -LiteralPath $artifactLandlock -Algorithm SHA256).Hash.ToLowerInvariant()
    if ([string]$artifact.landlockBinarySha256 -ne $landlockBinarySha256) {
        throw 'DeepSeek 编译产物中的 Landlock launcher 哈希不匹配'
    }
    $sourceMode = 'artifact'
} else {
    if ([string]::IsNullOrWhiteSpace($BaseBundle)) {
        $BaseBundle = Join-Path $PSScriptRoot "bundles/$($definition.BaseBundleName)"
    }
    $resolvedBaseBundle = [IO.Path]::GetFullPath($BaseBundle)
    if (-not (Test-Path -LiteralPath $resolvedBaseBundle -PathType Leaf)) {
        throw '没有可复用的 DeepSeek 编译产物或基础运行时包；仅此时才需要运行 compile-deepseek-harness-arm64.ps1'
    }
    $package = Assert-DeepSeekRuntimeBaseBundleCompatible `
        -Definition $definition `
        -BundlePath $resolvedBaseBundle
    $landlockBinarySha256 = [string]$package.landlockBinarySha256
    if ($landlockBinarySha256 -notmatch '^[a-f0-9]{64}$' -or
        -not (@(& tar -tzf $resolvedBaseBundle) -contains 'bin/landlock-run')) {
        throw '基础运行时包缺少经过记录的 Landlock launcher'
    }
    $sourceMode = 'bundle'
}

$configPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'deepseek/cordis.yml'))
if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) { throw "DeepSeek 配置不存在：$configPath" }
$pluginPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'deepseek/eleckoi-host-tools.mjs'))
if (-not (Test-Path -LiteralPath $pluginPath -PathType Leaf)) { throw "DeepSeek Android 工具插件不存在：$pluginPath" }
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null
$target = Join-Path $resolvedOutput $definition.BundleName

# A catalog-verified bundle with the same composition is already the final product. Copying it
# byte-for-byte avoids both the compiler and a pointless gzip/tar pass (and preserves its digest).
if ($sourceMode -eq 'bundle' -and -not $ForceRepackage) {
    $inspectionRoot = [IO.Path]::GetFullPath((Join-Path $resolvedOutput ".inspect-$([Guid]::NewGuid().ToString('N'))"))
    $outputBoundary = $resolvedOutput.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $inspectionRoot.StartsWith($outputBoundary, [StringComparison]::OrdinalIgnoreCase)) {
        throw '临时检查目录越出输出目录'
    }
    try {
        New-Item -ItemType Directory -Path $inspectionRoot | Out-Null
        & tar -xzf $resolvedBaseBundle -C $inspectionRoot 'etc/deepseek/cordis.yml'
        if ($LASTEXITCODE -ne 0) { throw '无法读取基础运行时包内的 DeepSeek 配置' }
        $archivedConfig = Join-Path $inspectionRoot 'etc/deepseek/cordis.yml'
        $configMatches = (Get-FileHash -LiteralPath $archivedConfig -Algorithm SHA256).Hash -eq
            (Get-FileHash -LiteralPath $configPath -Algorithm SHA256).Hash
        $pluginMatches = $false
        if ($configMatches) {
            & tar -xzf $resolvedBaseBundle -C $inspectionRoot 'etc/deepseek/eleckoi-host-tools.mjs' 2>$null
            if ($LASTEXITCODE -eq 0) {
                $archivedPlugin = Join-Path $inspectionRoot 'etc/deepseek/eleckoi-host-tools.mjs'
                $pluginMatches = (Get-FileHash -LiteralPath $archivedPlugin -Algorithm SHA256).Hash -eq
                    (Get-FileHash -LiteralPath $pluginPath -Algorithm SHA256).Hash
            }
        }
    } finally {
        if (Test-Path -LiteralPath $inspectionRoot) {
            $inspectionItem = Get-Item -LiteralPath $inspectionRoot -Force
            if (($inspectionItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "拒绝清理重解析点：$inspectionRoot"
            }
            Remove-Item -LiteralPath $inspectionRoot -Recurse -Force
        }
    }

    $catalog = Get-DeepSeekCatalogEntry -Definition $definition
    $baseHash = (Get-FileHash -LiteralPath $resolvedBaseBundle -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($configMatches -and $pluginMatches -and $baseHash -eq $catalog.Sha256) {
        if ([IO.Path]::GetFullPath($target) -ne $resolvedBaseBundle) {
            if (Test-Path -LiteralPath $target -PathType Leaf) {
                $targetHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
                if ($targetHash -ne $baseHash) {
                    throw "同名 Runtime 已存在但内容不同；请提升 bundleRevision：$target"
                }
            } else {
                Copy-Item -LiteralPath $resolvedBaseBundle -Destination $target
            }
        }
        $length = (Get-Item -LiteralPath $resolvedBaseBundle).Length
        Write-Host "配置与目录摘要均未变化，直接复用 Runtime：$target"
        Write-Host "SHA256：$baseHash"
        Write-Host "大小：$length bytes"
        return
    }
}

$temporaryName = "$($definition.BundleName).new-$([Guid]::NewGuid().ToString('N'))"
$temporary = Join-Path $resolvedOutput $temporaryName
$offlineCaStage = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '.cache/offline-ca-stage'))
New-Item -ItemType Directory -Force -Path $offlineCaStage | Out-Null
& (Join-Path $PSScriptRoot 'prepare-offline-ca.ps1') -StageRoot $offlineCaStage
if ($LASTEXITCODE -ne 0) { throw '准备离线 CA 证书失败' }

$containerScript = @'
set -euo pipefail
: "${SOURCE_ARTIFACT_PATH:=/eleckoi-source}"
: "${BASE_BUNDLE_PATH:=/eleckoi-base.egruntime}"
: "${CONFIG_PATH:=/eleckoi-config/cordis.yml}"
: "${PLUGIN_PATH:=/eleckoi-config/eleckoi-host-tools.mjs}"
: "${OFFLINE_CA_PATH:=/eleckoi-offline-ca}"
: "${OUTPUT_DIRECTORY:=/eleckoi-out}"
: "${PACKAGE_WORK_ROOT:=/tmp}"
mkdir -p "$PACKAGE_WORK_ROOT"
stage=$(mktemp -d "$PACKAGE_WORK_ROOT/eleckoi-deepseek-package.XXXXXX")
trap 'rm -rf "$stage"' EXIT
if [ "$SOURCE_MODE" = artifact ]; then
  cp -a "$SOURCE_ARTIFACT_PATH/bin" "$stage/bin"
  cp -a "$SOURCE_ARTIFACT_PATH/lib" "$stage/lib"
  cp -a "$SOURCE_ARTIFACT_PATH/licenses" "$stage/licenses"
else
  tar -xzf "$BASE_BUNDLE_PATH" -C "$stage"
fi
test -x "$stage/bin/dsh-jsonrpc-agent"
test -x "$stage/bin/landlock-run"
test -x "$stage/bin/rg"
echo "$LANDLOCK_BINARY_SHA256  $stage/bin/landlock-run" | sha256sum --check --strict
echo "$RIPGREP_BINARY_SHA256  $stage/bin/rg" | sha256sum --check --strict
mkdir -p "$stage/etc/deepseek"
cp "$CONFIG_PATH" "$stage/etc/deepseek/cordis.yml"
cp "$PLUGIN_PATH" "$stage/etc/deepseek/eleckoi-host-tools.mjs"
cp -a "$OFFLINE_CA_PATH/runtime-resources" "$stage/runtime-resources"
chmod 0644 "$stage/etc/deepseek/cordis.yml" "$stage/etc/deepseek/eleckoi-host-tools.mjs"
printf '%s\n' \
  '{' \
  '  "name": "deepseek-harness",' \
  "  \"version\": \"$HARNESS_VERSION\"," \
  "  \"sourceCommit\": \"$SOURCE_COMMIT\"," \
  "  \"sourcePatchSha256\": \"$SOURCE_PATCH_SHA256\"," \
  "  \"buildNodeVersion\": \"$BUILD_NODE_VERSION\"," \
  "  \"packagedNodeVersion\": \"$PACKAGED_NODE_VERSION\"," \
  "  \"pnpmVersion\": \"$PNPM_VERSION\"," \
  "  \"pkgVersion\": \"$PKG_VERSION\"," \
  "  \"ripgrepPackageVersion\": \"$RIPGREP_PACKAGE_VERSION\"," \
  "  \"ripgrepBinarySha256\": \"$RIPGREP_BINARY_SHA256\"," \
  "  \"landlockBinarySha256\": \"$LANDLOCK_BINARY_SHA256\"," \
  '  "entrypoint": "bin/dsh-jsonrpc-agent",' \
  '  "configPath": "etc/deepseek/cordis.yml"' \
  '}' > "$stage/deepseek-harness-package.json"
tar --format=gnu --sort=name --mtime='UTC 1970-01-01' --owner=0 --group=0 --numeric-owner \
  -C "$stage" -cf - bin deepseek-harness-package.json etc lib licenses runtime-resources \
  | gzip -n -9 > "$OUTPUT_DIRECTORY/$TEMPORARY_NAME"
'@

Write-Host "封装 DeepSeek Runtime；复用来源：$sourceMode"
if (-not $IsLinux) { throw 'DeepSeek Runtime 封装只支持 Linux ARM64 GitHub Actions runner' }
$bash = Get-Command bash -ErrorAction Stop
$nativeArchitecture = (& $bash.Source -lc 'uname -m').Trim()
if ($LASTEXITCODE -ne 0 -or $nativeArchitecture -notin @('aarch64', 'arm64')) {
    throw "原生主机架构不是 ARM64：$nativeArchitecture"
}
$packageWorkRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '.cache/package-work'))
$nativeEnvironment = [ordered]@{
    SOURCE_MODE = $sourceMode
    HARNESS_VERSION = $definition.HarnessVersion
    SOURCE_COMMIT = $definition.SourceCommit
    SOURCE_PATCH_SHA256 = $definition.SourcePatchSha256
    BUILD_NODE_VERSION = $definition.BuildNodeVersion
    PACKAGED_NODE_VERSION = $definition.PackagedNodeVersion
    PNPM_VERSION = $definition.PnpmVersion
    PKG_VERSION = $definition.PkgVersion
    RIPGREP_PACKAGE_VERSION = $definition.RipgrepPackageVersion
    RIPGREP_BINARY_SHA256 = $definition.RipgrepBinarySha256
    LANDLOCK_BINARY_SHA256 = $landlockBinarySha256
    TEMPORARY_NAME = $temporaryName
    SOURCE_ARTIFACT_PATH = $resolvedArtifact
    BASE_BUNDLE_PATH = $resolvedBaseBundle
    CONFIG_PATH = $configPath
    PLUGIN_PATH = $pluginPath
    OFFLINE_CA_PATH = $offlineCaStage
    OUTPUT_DIRECTORY = $resolvedOutput
    PACKAGE_WORK_ROOT = $packageWorkRoot
}
$previousEnvironment = @{}
try {
    foreach ($entry in $nativeEnvironment.GetEnumerator()) {
        $previousEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, 'Process')
    }
    & $bash.Source -lc $containerScript
    if ($LASTEXITCODE -ne 0) { throw "DeepSeek Runtime 原生封装失败，exitCode=$LASTEXITCODE" }
} finally {
    foreach ($entry in $previousEnvironment.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}
if (-not (Test-Path -LiteralPath $temporary -PathType Leaf)) { throw 'DeepSeek Runtime 临时产物不存在' }
Assert-SafeArchive -Path $temporary
$hash = (Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash.ToLowerInvariant()
$length = (Get-Item -LiteralPath $temporary).Length

if (Test-Path -LiteralPath $target -PathType Leaf) {
    $existingHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($existingHash -ne $hash) {
        throw "同名 Runtime 已存在但内容不同；请提升 bundleRevision。新产物保留在：$temporary"
    }
    Remove-Item -LiteralPath $temporary -Force
    Write-Host "封装结果与现有 Runtime 完全一致，继续复用：$target"
} else {
    Move-Item -LiteralPath $temporary -Destination $target
    Write-Host "Runtime 产物：$target"
}
Write-Host "SHA256：$hash"
Write-Host "大小：$length bytes"
