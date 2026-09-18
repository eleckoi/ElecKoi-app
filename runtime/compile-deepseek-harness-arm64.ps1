[CmdletBinding()]
param(
    [string]$ArtifactRoot = (Join-Path $PSScriptRoot 'artifacts'),
    [string]$CacheDirectory = (Join-Path $PSScriptRoot '.cache/deepseek-build')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Import-Module (Join-Path $PSScriptRoot 'deepseek/DeepSeekRuntimeBuild.psm1') -Force
$definition = Get-DeepSeekRuntimeBuildDefinition -RuntimeRoot $PSScriptRoot
$resolvedArtifactRoot = [IO.Path]::GetFullPath($ArtifactRoot)
$resolvedCache = [IO.Path]::GetFullPath($CacheDirectory)
$artifactPath = Join-Path $resolvedArtifactRoot $definition.ArtifactName
$artifactManifest = Join-Path $artifactPath 'deepseek-harness-build.json'
$artifactExecutable = Join-Path $artifactPath 'bin/dsh-jsonrpc-agent'
$artifactLandlock = Join-Path $artifactPath 'bin/landlock-run'
$artifactLandlockLicense = Join-Path $artifactPath 'licenses/landlock-run/LICENSE'
$artifactRipgrep = Join-Path $artifactPath 'bin/rg'
$artifactRipgrepLicense = Join-Path $artifactPath 'licenses/ripgrep/LICENSE'
$artifactSharpLibraries = Join-Path $artifactPath 'lib/sharp'
$artifactSharpLicense = Join-Path $artifactPath 'licenses/sharp-libvips/LGPL-3.0.txt'
$sourcePatchPath = $definition.SourcePatchPath
$sharpLibvipsLicensePath = Join-Path $PSScriptRoot 'licenses/talloc-LGPL-3.0.txt'
if (-not (Test-Path -LiteralPath $sharpLibvipsLicensePath -PathType Leaf)) {
    throw "sharp/libvips LGPL-3.0 许可证文本不存在：$sharpLibvipsLicensePath"
}

if (Test-Path -LiteralPath $artifactManifest -PathType Leaf) {
    $existing = Get-Content -Raw -LiteralPath $artifactManifest | ConvertFrom-Json
    if ([string]$existing.sourceCommit -eq $definition.SourceCommit -and
        [string]$existing.sourcePatchSha256 -eq $definition.SourcePatchSha256 -and
        [string]$existing.version -eq $definition.HarnessVersion -and
        [string]$existing.buildNodeVersion -eq $definition.BuildNodeVersion -and
        [string]$existing.packagedNodeVersion -eq $definition.PackagedNodeVersion -and
        [string]$existing.pnpmVersion -eq $definition.PnpmVersion -and
        [string]$existing.pkgVersion -eq $definition.PkgVersion -and
        [string]$existing.ripgrepPackageVersion -eq $definition.RipgrepPackageVersion -and
        [string]$existing.ripgrepBinarySha256 -eq $definition.RipgrepBinarySha256 -and
        (Test-Path -LiteralPath $artifactExecutable -PathType Leaf) -and
        (Test-Path -LiteralPath $artifactLandlock -PathType Leaf) -and
        (Test-Path -LiteralPath $artifactLandlockLicense -PathType Leaf) -and
        (Test-Path -LiteralPath $artifactRipgrep -PathType Leaf) -and
        (Test-Path -LiteralPath $artifactRipgrepLicense -PathType Leaf) -and
        (Test-Path -LiteralPath $artifactSharpLibraries -PathType Container) -and
        (@(Get-ChildItem -LiteralPath $artifactSharpLibraries -File -Filter 'libvips-cpp.so.*').Count -eq 1) -and
        (Test-Path -LiteralPath $artifactSharpLicense -PathType Leaf) -and
        (Get-Item -LiteralPath $artifactExecutable).Length -gt 100MB -and
        [string]$existing.landlockBinarySha256 -eq
            (Get-FileHash -LiteralPath $artifactLandlock -Algorithm SHA256).Hash.ToLowerInvariant()) {
        Write-Host "复用已编译的 DeepSeek Harness：$artifactPath"
        return
    }
    throw "已有编译产物与构建清单不一致；源码或工具链变化时请提升 binaryRevision 后重新构建：$artifactPath"
}
if (Test-Path -LiteralPath $artifactPath) {
    throw "编译产物目录已存在但不完整；请检查后处理：$artifactPath"
}

New-Item -ItemType Directory -Force -Path $resolvedArtifactRoot, $resolvedCache | Out-Null

$containerScript = @'
set -euo pipefail
: "${ELECKOI_OUT:=/eleckoi-out}"
: "${ELECKOI_CACHE:=/eleckoi-cache}"
: "${ELECKOI_NODE:=/opt/node}"
: "${ELECKOI_WORK:=/work}"
: "${SOURCE_PATCH_PATH:=/eleckoi-patches/source.patch}"
export PATH="$ELECKOI_NODE/bin:$PATH"
export COREPACK_HOME="$ELECKOI_CACHE/corepack"
export npm_config_devdir="$ELECKOI_CACHE/node-gyp"
# The full DSH project-reference graph can exceed Node's default old-space limit.
# Keep enough headroom for the 8 GiB native ARM64 CI runner.
export NODE_OPTIONS=--max-old-space-size=6144
mkdir -p "$ELECKOI_OUT" "$ELECKOI_CACHE/downloads" "$ELECKOI_CACHE/pnpm-store" \
  "$ELECKOI_CACHE/pkg/sea" "$ELECKOI_CACHE/home/.pkg-cache" "$ELECKOI_NODE" "$ELECKOI_WORK"
export HOME="$ELECKOI_CACHE/home"

download_verified() {
  url="$1"
  expected="$2"
  target="$3"
  if [ -f "$target" ] && echo "$expected  $target" | sha256sum --check --strict --status; then
    echo "cache hit: $target"
    return
  fi
  rm -f "$target" "$target.part"
  curl --fail --location --retry 4 --output "$target.part" "$url"
  echo "$expected  $target.part" | sha256sum --check --strict
  mv "$target.part" "$target"
}

source_cache="$ELECKOI_CACHE/deepseek-harness.git"
if [ ! -d "$source_cache" ]; then
  git init --bare "$source_cache"
  git -c safe.directory="$source_cache" -C "$source_cache" remote add origin "$SOURCE_REPOSITORY"
else
  git -c safe.directory="$source_cache" -C "$source_cache" remote set-url origin "$SOURCE_REPOSITORY"
fi
# `eleckoi-build` is a cache-local disposable ref. A later pinned release is not guaranteed to
# descend from the commit restored by actions/cache, so update this one ref explicitly instead of
# deleting the verified source cache or requiring a fast-forward relationship between releases.
git -c safe.directory="$source_cache" -C "$source_cache" fetch --depth 1 origin \
  "+$SOURCE_COMMIT:refs/heads/eleckoi-build"
build_root=$(mktemp -d "$ELECKOI_WORK/eleckoi-dsh.XXXXXX")
trap 'rm -rf "$build_root"' EXIT
git -c safe.directory="$source_cache" clone --no-checkout "$source_cache" "$build_root/source"
cd "$build_root/source"
git checkout --detach "$SOURCE_COMMIT"
test "$(git rev-parse HEAD)" = "$SOURCE_COMMIT"
echo "$SOURCE_PATCH_SHA256  $SOURCE_PATCH_PATH" | sha256sum --check --strict
git apply --check --unidiff-zero "$SOURCE_PATCH_PATH"
git apply --unidiff-zero "$SOURCE_PATCH_PATH"
patched_dependency_count=0
while IFS= read -r patched_dependency; do
  patched_dependency_count=$((patched_dependency_count + 1))
  test -f "$patched_dependency" || {
    echo "patchedDependencies references a missing file: $patched_dependency" >&2
    exit 1
  }
done < <(sed -nE 's/^[[:space:]]+[^:]+:[[:space:]]+(patches\/[^[:space:]]+\.patch)[[:space:]]*$/\1/p' pnpm-workspace.yaml)
test "$patched_dependency_count" -gt 0

build_node_archive="$ELECKOI_CACHE/downloads/node-v$BUILD_NODE_VERSION-linux-arm64.tar.gz"
download_verified \
  "https://nodejs.org/download/release/v$BUILD_NODE_VERSION/node-v$BUILD_NODE_VERSION-linux-arm64.tar.gz" \
  "$BUILD_NODE_SHA256" "$build_node_archive"
tar -xzf "$build_node_archive" --strip-components=1 -C "$ELECKOI_NODE"
node --version
corepack enable
corepack prepare "pnpm@$PNPM_VERSION" --activate
pnpm config set store-dir "$ELECKOI_CACHE/pnpm-store"
pnpm --version
pnpm install --frozen-lockfile

# The single-executable packager embeds JavaScript but cannot materialize an executable npm asset.
# Build the reviewed Landlock launcher as a physical ARM64 sidecar for /opt/eleckoi/bin.
landlock_source="$build_root/source/native/system/packages/entry/src/main.c"
landlock_product="$build_root/landlock-run"
cc -std=c11 -Os -Wall -Wextra -Werror -s -o "$landlock_product" "$landlock_source"
test -x "$landlock_product"

ripgrep_archive="$ELECKOI_CACHE/downloads/vscode-ripgrep-linux-arm64-$RIPGREP_PACKAGE_VERSION.tgz"
download_verified "$RIPGREP_ARCHIVE_URL" "$RIPGREP_ARCHIVE_SHA256" "$ripgrep_archive"
ripgrep_stage="$build_root/ripgrep"
mkdir -p "$ripgrep_stage"
tar -xzf "$ripgrep_archive" -C "$ripgrep_stage"
ripgrep_binary="$ripgrep_stage/package/bin/rg"
test -x "$ripgrep_binary"
echo "$RIPGREP_BINARY_SHA256  $ripgrep_binary" | sha256sum --check --strict

# Upstream accepts only a Node major although its pkg backend supports exact versions.
# Widen that one parser and seed pkg with our independently verified exact Node archive.
node --input-type=module <<'NODE'
import { readFileSync, writeFileSync } from 'node:fs'
const path = 'scripts/build-exe-for-python-sdk.ts'
const source = readFileSync(path, 'utf8')
const from = 'if (!/^node\\d+$/.test(nodeRange)) {'
const to = 'if (!/^node\\d+(?:\\.\\d+){0,2}$/.test(nodeRange)) {'
if (source.split(from).length !== 2) throw new Error('DeepSeek Harness target parser changed upstream')
const rootPackage = JSON.parse(readFileSync('package.json', 'utf8'))
if (rootPackage.devDependencies?.['@yao-pkg/pkg'] !== process.env.PKG_VERSION) {
  throw new Error('DeepSeek Harness pkg version changed upstream')
}
writeFileSync(path, source.replace(from, to))
NODE
pkg_archive="$ELECKOI_CACHE/pkg/sea/node-v$PACKAGED_NODE_VERSION-linux-arm64.tar.gz"
download_verified \
  "https://nodejs.org/download/release/v$PACKAGED_NODE_VERSION/node-v$PACKAGED_NODE_VERSION-linux-arm64.tar.gz" \
  "$PACKAGED_NODE_SHA256" "$pkg_archive"
ln -sfn "$ELECKOI_CACHE/pkg/sea" "$HOME/.pkg-cache/sea"
: > "$pkg_archive.ok"
pnpm exec tsx scripts/build-exe-for-python-sdk.ts \
  --targets="node$PACKAGED_NODE_VERSION-linux-arm64"

product="$build_root/source/dist-exe/deepseek-harness-sdk-runtime-linux-arm64"
test -x "$product"
sharp_package="$build_root/source/python/sdk-runtime/src/deepseek_harness_runtime/runtime/node/node_modules/@img/sharp-libvips-linux-arm64"
test -d "$sharp_package/lib"
test -f "$SHARP_LIBVIPS_LICENSE_PATH"
sharp_libraries=$(find "$sharp_package/lib" -maxdepth 1 -type f -name 'libvips-cpp.so.*' -print)
test "$(printf '%s\n' "$sharp_libraries" | sed '/^$/d' | wc -l)" -eq 1
stage="$ELECKOI_OUT/$ARTIFACT_NAME.partial"
final="$ELECKOI_OUT/$ARTIFACT_NAME"
test ! -e "$stage" && test ! -e "$final"
mkdir -p "$stage/bin" "$stage/lib/sharp" "$stage/licenses/deepseek-harness" \
  "$stage/licenses/landlock-run" "$stage/licenses/node" "$stage/licenses/ripgrep" \
  "$stage/licenses/sharp-libvips"
cp "$product" "$stage/bin/dsh-jsonrpc-agent"
cp "$landlock_product" "$stage/bin/landlock-run"
cp $sharp_libraries "$stage/lib/sharp/"
cp "$ripgrep_binary" "$stage/bin/rg"
chmod 0755 "$stage/bin/dsh-jsonrpc-agent" "$stage/bin/landlock-run" "$stage/bin/rg"
cp LICENSE "$stage/licenses/deepseek-harness/LICENSE"
cp THIRD_PARTY_NOTICES.md "$stage/licenses/deepseek-harness/THIRD_PARTY_NOTICES.md"
cp native/system/LICENSE "$stage/licenses/landlock-run/LICENSE"
cp "$ripgrep_stage/package/LICENSE" "$stage/licenses/ripgrep/LICENSE"
cp "$SHARP_LIBVIPS_LICENSE_PATH" "$stage/licenses/sharp-libvips/LGPL-3.0.txt"
tar -xOf "$pkg_archive" "node-v$PACKAGED_NODE_VERSION-linux-arm64/LICENSE" > "$stage/licenses/node/LICENSE"
test -s "$stage/licenses/node/LICENSE"
printf '%s\n' \
  '{' \
  '  "schemaVersion": 1,' \
  '  "name": "deepseek-harness-build",' \
  "  \"version\": \"$HARNESS_VERSION\"," \
  "  \"sourceCommit\": \"$SOURCE_COMMIT\"," \
  "  \"sourcePatchSha256\": \"$SOURCE_PATCH_SHA256\"," \
  "  \"buildNodeVersion\": \"$BUILD_NODE_VERSION\"," \
  "  \"packagedNodeVersion\": \"$PACKAGED_NODE_VERSION\"," \
  "  \"pnpmVersion\": \"$PNPM_VERSION\"," \
  "  \"pkgVersion\": \"$PKG_VERSION\"," \
  "  \"ripgrepPackageVersion\": \"$RIPGREP_PACKAGE_VERSION\"," \
  "  \"ripgrepBinarySha256\": \"$RIPGREP_BINARY_SHA256\"," \
  "  \"landlockBinarySha256\": \"$(sha256sum "$stage/bin/landlock-run" | cut -d' ' -f1)\"," \
  '  "entrypoint": "bin/dsh-jsonrpc-agent"' \
  '}' > "$stage/deepseek-harness-build.json"
sha256sum "$stage/bin/dsh-jsonrpc-agent"
sha256sum "$stage/bin/landlock-run"
sha256sum "$stage/bin/rg"
mv "$stage" "$final"
'@

Write-Host "编译 DeepSeek Harness $($definition.HarnessVersion) ($($definition.SourceCommit))"
if (-not $IsLinux) { throw 'DeepSeek Harness 编译只支持 Linux ARM64 GitHub Actions runner' }
$bash = Get-Command bash -ErrorAction Stop
$nativeArchitecture = (& $bash.Source -lc 'uname -m').Trim()
if ($LASTEXITCODE -ne 0 -or $nativeArchitecture -notin @('aarch64', 'arm64')) {
    throw "原生主机架构不是 ARM64：$nativeArchitecture"
}
$nativeEnvironment = [ordered]@{
    SOURCE_REPOSITORY = $definition.SourceRepository
    SOURCE_COMMIT = $definition.SourceCommit
    SOURCE_PATCH_SHA256 = $definition.SourcePatchSha256
    SOURCE_PATCH_PATH = $sourcePatchPath
    SHARP_LIBVIPS_LICENSE_PATH = $sharpLibvipsLicensePath
    HARNESS_VERSION = $definition.HarnessVersion
    BUILD_NODE_VERSION = $definition.BuildNodeVersion
    BUILD_NODE_SHA256 = $definition.BuildNodeSha256
    PACKAGED_NODE_VERSION = $definition.PackagedNodeVersion
    PACKAGED_NODE_SHA256 = $definition.PackagedNodeSha256
    PNPM_VERSION = $definition.PnpmVersion
    PKG_VERSION = $definition.PkgVersion
    RIPGREP_PACKAGE_VERSION = $definition.RipgrepPackageVersion
    RIPGREP_ARCHIVE_URL = $definition.RipgrepArchiveUrl
    RIPGREP_ARCHIVE_SHA256 = $definition.RipgrepArchiveSha256
    RIPGREP_BINARY_SHA256 = $definition.RipgrepBinarySha256
    ARTIFACT_NAME = $definition.ArtifactName
    ELECKOI_OUT = $resolvedArtifactRoot
    ELECKOI_CACHE = $resolvedCache
    ELECKOI_NODE = (Join-Path $resolvedCache 'node')
    ELECKOI_WORK = (Join-Path $resolvedCache 'work')
}
$previousEnvironment = @{}
try {
    foreach ($entry in $nativeEnvironment.GetEnumerator()) {
        $previousEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, 'Process')
    }
    Write-Host "原生 ARM64 架构：$nativeArchitecture；构建缓存：$resolvedCache"
    & $bash.Source -lc $containerScript
    if ($LASTEXITCODE -ne 0) { throw "DeepSeek Harness 原生 ARM64 编译失败，exitCode=$LASTEXITCODE" }
} finally {
    foreach ($entry in $previousEnvironment.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}
if (-not (Test-Path -LiteralPath $artifactExecutable -PathType Leaf)) {
    throw "DeepSeek Harness ARM64 编译产物不存在：$artifactExecutable"
}
if (-not (Test-Path -LiteralPath $artifactLandlock -PathType Leaf)) {
    throw "DeepSeek Harness ARM64 Landlock 产物不存在：$artifactLandlock"
}
if (-not (Test-Path -LiteralPath $artifactLandlockLicense -PathType Leaf)) {
    throw "DeepSeek Harness ARM64 Landlock 许可证不存在：$artifactLandlockLicense"
}
if (-not (Test-Path -LiteralPath $artifactRipgrep -PathType Leaf)) {
    throw "DeepSeek Harness ARM64 ripgrep 产物不存在：$artifactRipgrep"
}
if (-not (Test-Path -LiteralPath $artifactRipgrepLicense -PathType Leaf)) {
    throw "DeepSeek Harness ARM64 ripgrep 许可证不存在：$artifactRipgrepLicense"
}
if (-not (Test-Path -LiteralPath $artifactSharpLibraries -PathType Container) -or
    @(Get-ChildItem -LiteralPath $artifactSharpLibraries -File -Filter 'libvips-cpp.so.*').Count -ne 1) {
    throw "DeepSeek Harness ARM64 sharp/libvips 原生库不存在：$artifactSharpLibraries"
}
if (-not (Test-Path -LiteralPath $artifactSharpLicense -PathType Leaf)) {
    throw "DeepSeek Harness ARM64 sharp/libvips 许可证不存在：$artifactSharpLicense"
}
Write-Host "编译产物：$artifactPath"
