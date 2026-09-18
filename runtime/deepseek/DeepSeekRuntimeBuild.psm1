Set-StrictMode -Version Latest

function Get-DeepSeekRuntimeBuildDefinition {
    [CmdletBinding()]
    param(
        [string]$RuntimeRoot = (Split-Path -Parent $PSScriptRoot)
    )

    $resolvedRuntimeRoot = [IO.Path]::GetFullPath($RuntimeRoot)
    $manifestPath = Join-Path $resolvedRuntimeRoot 'deepseek/build-manifest.json'
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "DeepSeek Harness 构建清单不存在：$manifestPath"
    }
    $manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
    if ([int]$manifest.schemaVersion -ne 1) { throw '不支持的 DeepSeek Harness 构建清单版本' }

    $safeVersion = '^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$'
    $sha256 = '^[a-f0-9]{64}$'
    $sourceCommit = '^[a-f0-9]{40}$'
    $releaseRepository = '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$'
    $releaseTag = '^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$'
    foreach ($property in @('harnessVersion', 'binaryRevision', 'bundleRevision', 'buildNodeVersion', 'packagedNodeVersion', 'pnpmVersion', 'pkgVersion', 'ripgrepPackageVersion')) {
        $value = [string]$manifest.$property
        if ($value -notmatch $safeVersion) { throw "DeepSeek Harness 构建字段无效：$property" }
    }
    $baseBundleRevision = [string]$manifest.baseBundleRevision
    if ([string]::IsNullOrWhiteSpace($baseBundleRevision)) {
        $baseBundleRevision = [string]$manifest.binaryRevision
    }
    if ($baseBundleRevision -notmatch $safeVersion) {
        throw 'DeepSeek Harness 基础 Bundle revision 无效'
    }
    if ([string]$manifest.sourceRepository -notmatch '^https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\.git$') {
        throw 'DeepSeek Harness 源码仓库地址无效'
    }
    if ([string]$manifest.sourceCommit -notmatch $sourceCommit) { throw 'DeepSeek Harness 源码提交无效' }
    foreach ($property in @('buildNodeSha256', 'packagedNodeSha256', 'sourcePatchSha256', 'ripgrepArchiveSha256', 'ripgrepBinarySha256')) {
        if ([string]$manifest.$property -notmatch $sha256) { throw "DeepSeek Harness 哈希无效：$property" }
    }
    if ([string]$manifest.ripgrepArchiveUrl -notmatch '^https://registry\.npmjs\.org/@vscode/ripgrep-linux-arm64/-/ripgrep-linux-arm64-[A-Za-z0-9._-]+\.tgz$') {
        throw 'DeepSeek Harness ripgrep 归档地址无效'
    }
    $sourcePatchPath = [IO.Path]::GetFullPath((Join-Path $resolvedRuntimeRoot ([string]$manifest.sourcePatch)))
    $patchRoot = [IO.Path]::GetFullPath((Join-Path $resolvedRuntimeRoot 'deepseek/patches'))
    if (-not $sourcePatchPath.StartsWith($patchRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath $sourcePatchPath -PathType Leaf)) {
        throw 'DeepSeek Harness 源码补丁路径无效'
    }
    $actualPatchSha256 = (Get-FileHash -LiteralPath $sourcePatchPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualPatchSha256 -ne [string]$manifest.sourcePatchSha256) {
        throw 'DeepSeek Harness 源码补丁哈希不匹配'
    }
    if ([string]$manifest.releaseRepository -notmatch $releaseRepository) { throw 'DeepSeek Harness Release 仓库无效' }
    if ([string]$manifest.releaseTag -notmatch $releaseTag) { throw 'DeepSeek Harness Release 标签无效' }

    $binaryBaseName = "deepseek-harness-$($manifest.harnessVersion)-$($manifest.binaryRevision)-arm64"
    $baseBundleBaseName = "deepseek-harness-$($manifest.harnessVersion)-$baseBundleRevision-arm64"
    $bundleBaseName = "deepseek-harness-$($manifest.harnessVersion)-$($manifest.bundleRevision)-arm64"
    [PSCustomObject]@{
        RuntimeRoot = $resolvedRuntimeRoot
        ManifestPath = $manifestPath
        SourceRepository = [string]$manifest.sourceRepository
        SourceCommit = [string]$manifest.sourceCommit
        SourcePatchPath = $sourcePatchPath
        SourcePatchSha256 = [string]$manifest.sourcePatchSha256
        HarnessVersion = [string]$manifest.harnessVersion
        BinaryRevision = [string]$manifest.binaryRevision
        BaseBundleRevision = $baseBundleRevision
        BundleRevision = [string]$manifest.bundleRevision
        BuildNodeVersion = [string]$manifest.buildNodeVersion
        BuildNodeSha256 = [string]$manifest.buildNodeSha256
        PackagedNodeVersion = [string]$manifest.packagedNodeVersion
        PackagedNodeSha256 = [string]$manifest.packagedNodeSha256
        PnpmVersion = [string]$manifest.pnpmVersion
        PkgVersion = [string]$manifest.pkgVersion
        RipgrepPackageVersion = [string]$manifest.ripgrepPackageVersion
        RipgrepArchiveUrl = [string]$manifest.ripgrepArchiveUrl
        RipgrepArchiveSha256 = [string]$manifest.ripgrepArchiveSha256
        RipgrepBinarySha256 = [string]$manifest.ripgrepBinarySha256
        ReleaseRepository = [string]$manifest.releaseRepository
        ReleaseTag = [string]$manifest.releaseTag
        ArtifactName = "$binaryBaseName-build"
        BinaryBundleName = "$binaryBaseName.egruntime"
        BaseBundleName = "$baseBundleBaseName.egruntime"
        BaseReleaseTag = "deepseek-harness-$($manifest.harnessVersion)-$baseBundleRevision"
        BundleName = "$bundleBaseName.egruntime"
        ReleaseUrl = "https://github.com/$($manifest.releaseRepository)/releases/download/$($manifest.releaseTag)/$bundleBaseName.egruntime"
    }
}

function Get-DeepSeekCatalogEntry {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Definition
    )

    $catalogPath = Join-Path $Definition.RuntimeRoot 'catalog/runtime-catalog.json'
    $catalog = Get-Content -Raw -LiteralPath $catalogPath | ConvertFrom-Json
    $deepSeek = $catalog.harnesses.deepseek
    if ($null -eq $deepSeek) { throw 'Runtime Catalog 未声明 DeepSeek Harness' }
    if ([string]$deepSeek.version -ne $Definition.HarnessVersion -or
        [string]$deepSeek.sourceCommit -ne $Definition.SourceCommit -or
        [string]$deepSeek.assetPath -ne $Definition.BundleName) {
        throw 'DeepSeek 构建清单与 Runtime Catalog 不一致'
    }
    if ([string]$deepSeek.sha256 -notmatch '^[a-f0-9]{64}$' -or
        [long]$deepSeek.archiveBytesLimit -le 0) {
        throw 'Runtime Catalog 的 DeepSeek 摘要或大小限制无效'
    }
    [PSCustomObject]@{
        CatalogPath = $catalogPath
        Sha256 = ([string]$deepSeek.sha256).ToLowerInvariant()
        ArchiveBytesLimit = [long]$deepSeek.archiveBytesLimit
        AssetPath = [string]$deepSeek.assetPath
    }
}

function Assert-DeepSeekRuntimeBaseBundleCompatible {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Definition,
        [Parameter(Mandatory)][string]$BundlePath
    )

    $resolvedBundle = [IO.Path]::GetFullPath($BundlePath)
    if (-not (Test-Path -LiteralPath $resolvedBundle -PathType Leaf)) {
        throw "DeepSeek 基础 Runtime 不存在：$resolvedBundle"
    }

    $entries = @(& tar -tzf $resolvedBundle)
    if ($LASTEXITCODE -ne 0 -or $entries.Count -eq 0) {
        throw "DeepSeek 基础 Runtime 不是有效的 tar.gz：$resolvedBundle"
    }
    foreach ($rawEntry in $entries) {
        $entry = ([string]$rawEntry).Replace('\', '/').TrimEnd('/')
        if ([string]::IsNullOrWhiteSpace($entry) -or $entry -in @('.', './') -or
            $entry.StartsWith('/') -or $entry -match '^[A-Za-z]:' -or
            @($entry -split '/') -contains '..') {
            throw "DeepSeek 基础 Runtime 包含不安全路径：$rawEntry"
        }
    }

    $encodedPackage = & tar -xOf $resolvedBundle 'deepseek-harness-package.json'
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($encodedPackage -join "`n"))) {
        throw 'DeepSeek 基础 Runtime 缺少构建身份元数据'
    }
    $package = $encodedPackage | ConvertFrom-Json
    $expectedIdentity = [ordered]@{
        version = $Definition.HarnessVersion
        sourceCommit = $Definition.SourceCommit
        sourcePatchSha256 = $Definition.SourcePatchSha256
        buildNodeVersion = $Definition.BuildNodeVersion
        packagedNodeVersion = $Definition.PackagedNodeVersion
        pnpmVersion = $Definition.PnpmVersion
        pkgVersion = $Definition.PkgVersion
        ripgrepPackageVersion = $Definition.RipgrepPackageVersion
        ripgrepBinarySha256 = $Definition.RipgrepBinarySha256
    }
    foreach ($identity in $expectedIdentity.GetEnumerator()) {
        if ([string]$package.($identity.Key) -ne [string]$identity.Value) {
            throw "DeepSeek 基础 Runtime 构建身份不匹配：$($identity.Key)"
        }
    }
    return $package
}

Export-ModuleMember -Function `
    Get-DeepSeekRuntimeBuildDefinition, `
    Get-DeepSeekCatalogEntry, `
    Assert-DeepSeekRuntimeBaseBundleCompatible
