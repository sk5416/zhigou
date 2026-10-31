#requires -Version 5.1
param(
    [Parameter(Mandatory=$true)][string]$KeystorePath,
    [Parameter(Mandatory=$true)][string]$KeyAlias
)
$ErrorActionPreference = 'Stop'
$projectPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$keyPath = (Resolve-Path -LiteralPath $KeystorePath).Path
if ($keyPath.StartsWith($projectPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw '签名密钥必须保存在项目目录之外。'
}
if (!$env:ZHIGOU_STORE_PASSWORD -or !$env:ZHIGOU_KEY_PASSWORD) { throw '请通过ZHIGOU_STORE_PASSWORD和ZHIGOU_KEY_PASSWORD提供口令；脚本不会记录口令。' }
$sdkPath = $env:ANDROID_HOME
if (!$sdkPath) { throw '请设置ANDROID_HOME。' }
$buildToolsPath = Join-Path $sdkPath 'build-tools/35.0.0'
Push-Location $projectPath
try {
    & .\gradlew.bat testReleaseUnitTest lintRelease assembleRelease
    if ($LASTEXITCODE -ne 0) { throw '正式构建检查未通过，未签名。' }
    $unsigned = 'app/build/outputs/apk/release/app-release-unsigned.apk'
    $badging = & (Join-Path $buildToolsPath 'aapt.exe') dump badging $unsigned
    if ($LASTEXITCODE -ne 0) { throw '无法读取APK信息。' }
    $match = [regex]::Match(($badging -join "`n"), "versionName='(\d+\.\d+\.\d+)'")
    if (!$match.Success) { throw '应用版本仍含开发标记；仅正式版本元数据可以执行签名交付。' }
    if (($badging -join "`n") -notmatch "package: name='com.sk5416.zhigou'") { throw '应用标识不匹配。' }
    $permissions = & (Join-Path $buildToolsPath 'aapt.exe') dump permissions $unsigned
    if ($LASTEXITCODE -ne 0 -or ($permissions -join "`n") -match 'android.permission.INTERNET') { throw '权限检查未通过。' }
    New-Item -ItemType Directory -Path '.artifacts/release' -Force | Out-Null
    $target = '.artifacts/release/zhigou-' + $match.Groups[1].Value + '.apk'
    if (Test-Path -LiteralPath $target) { throw '目标发布包已存在，请先核对已交付版本，禁止直接覆盖。' }
    $aligned = '.artifacts/release/aligned-' + [guid]::NewGuid().ToString('N') + '.apk'
    & (Join-Path $buildToolsPath 'zipalign.exe') -P 16 4 $unsigned $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK对齐失败。' }
    & (Join-Path $buildToolsPath 'apksigner.bat') sign --ks $keyPath --ks-key-alias $KeyAlias --ks-pass env:ZHIGOU_STORE_PASSWORD --key-pass env:ZHIGOU_KEY_PASSWORD --out $target $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK签名失败，不得分发该文件。' }
    & (Join-Path $buildToolsPath 'apksigner.bat') verify --verbose --print-certs $target
    if ($LASTEXITCODE -ne 0) { throw '签名验证失败。' }
    $digest = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText((Join-Path $projectPath ($target + '.sha256')), ($digest + '  ' + [IO.Path]::GetFileName($target) + "`n"), [Text.UTF8Encoding]::new($false))
    Write-Output ('签名包与校验文件已生成：' + $target)
} finally { Pop-Location }
