$ErrorActionPreference = 'Stop'
$envPath = Join-Path $PSScriptRoot '.env'
if (Test-Path -LiteralPath $envPath) {
    Write-Output '.env already exists; no changes made.'
    exit 0
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '.env.example') -Destination $envPath
Write-Output 'Created .env template. Fill all credentials before starting. Never commit .env.'
