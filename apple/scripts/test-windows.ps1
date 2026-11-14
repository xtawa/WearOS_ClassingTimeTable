param([switch]$ParseOnly)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$toolchain = Join-Path $root '.tools\Swift\Toolchains\6.4.2+NoAsserts\usr\bin'
$sdk = Join-Path $root '.tools\Swift\Platforms\6.4.2\Windows.platform\Developer\SDKs\Windows.sdk'
if (Test-Path $toolchain) {
    $runtime = Join-Path $root '.tools\Swift\Runtimes\6.4.2\usr\bin'
    $xctest = Join-Path $root '.tools\Swift\Platforms\6.4.2\Windows.platform\Developer\Library\XCTest-6.4.2\usr\bin64'
    $testing = Join-Path $root '.tools\Swift\Platforms\6.4.2\Windows.platform\Developer\Library\Testing-6.4.2\usr\bin'
    $manifest = Join-Path $toolchain '..\lib\swift\pm\ManifestAPI'
    $env:PATH = "$toolchain;$runtime;$xctest;$testing;$manifest;$env:PATH"
    $env:SDKROOT = $sdk
}
if ($ParseOnly) {
    # Syntax only: Windows does not contain SwiftUI or WatchKit SDK modules.
    $sources = Get-ChildItem "$root\iOS", "$root\watchOS", "$root\Shared", "$root\Widgets", "$root\ClassingCore\Sources" -Filter '*.swift' -Recurse | ForEach-Object FullName
    & swiftc -frontend -parse $sources
} else { & swift test --package-path "$root\ClassingCore" }
exit $LASTEXITCODE
