# Run on your Windows PC after installing GitHub CLI and downloading the three signing files.
# Never commit signing files or print secret values.
param(
    [string]$Folder = ".",
    [string]$Repo = "Sheff1981/boostlab-android"
)
$ErrorActionPreference = "Stop"
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) is missing. Install it with: winget install --id GitHub.cli"
}
& gh auth status 1>$null
if ($LASTEXITCODE -ne 0) { throw "Sign in first: gh auth login" }
$base64 = Join-Path $Folder "boostlab-keystore-base64.txt"
$credentials = Join-Path $Folder "boostlab-signing-credentials.txt"
$keystore = Join-Path $Folder "boostlab-release.jks"
foreach ($file in @($base64, $credentials, $keystore)) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Missing file: $file" }
}
$raw = [IO.File]::ReadAllText((Resolve-Path $base64)).Trim()
$decoded = [Convert]::FromBase64String($raw)
$actual = [IO.File]::ReadAllBytes((Resolve-Path $keystore))
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $decodedHash = [BitConverter]::ToString($sha.ComputeHash($decoded))
    $actualHash = [BitConverter]::ToString($sha.ComputeHash($actual))
} finally { $sha.Dispose() }
if ($decodedHash -ne $actualHash) {
    throw "Keystore and Base64 file do not match."
}
$vars = @{}
Get-Content -LiteralPath $credentials | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') { $vars[$Matches[1]] = $Matches[2].Trim() }
}
foreach ($key in @("BOOSTLAB_KEYSTORE_PASSWORD", "BOOSTLAB_KEY_ALIAS", "BOOSTLAB_KEY_PASSWORD")) {
    if (-not $vars.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($vars[$key])) {
        throw "Missing credential: $key"
    }
}
$raw | gh secret set BOOSTLAB_KEYSTORE_BASE64 --repo $Repo
if ($LASTEXITCODE -ne 0) { throw "Failed to configure keystore secret" }
foreach ($key in @("BOOSTLAB_KEYSTORE_PASSWORD", "BOOSTLAB_KEY_ALIAS", "BOOSTLAB_KEY_PASSWORD")) {
    $vars[$key] | gh secret set $key --repo $Repo
    if ($LASTEXITCODE -ne 0) { throw "Failed to configure $key" }
}
Write-Host "Four GitHub signing secrets configured. Do not delete your keystore backup."
