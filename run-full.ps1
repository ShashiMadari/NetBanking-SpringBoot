$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $project '.env'))) {
    throw 'Create .env with prepare-podman-env.ps1, then fill all runtime settings including ORACLE_PWD.'
}
if ($args -contains '--build') { throw 'This wrapper runs published images. Use pull and up -d without --build.' }
$venv = Join-Path $project '.venv'
$provider = Join-Path $venv 'Scripts\podman-compose.exe'
if (-not (Test-Path -LiteralPath $provider)) {
    py -m venv $venv
    if ($LASTEXITCODE -ne 0) { throw 'Python environment creation failed.' }
    & (Join-Path $venv 'Scripts\python.exe') -m pip install 'podman-compose==1.6.0'
    if ($LASTEXITCODE -ne 0) { throw 'Compose provider installation failed.' }
}
$podmanCommand = Get-Command podman -ErrorAction SilentlyContinue
if (-not $podmanCommand) {
    $candidate = Join-Path $env:LOCALAPPDATA 'Programs\Podman\podman.exe'
    if (-not (Test-Path -LiteralPath $candidate)) { throw 'Podman CLI was not found.' }
    $env:PATH = (Split-Path $candidate -Parent) + ';' + $env:PATH
}
Push-Location $project
try {
    & $provider --env-file .env -f compose.full.yaml -p o-bank-full @args
    exit $LASTEXITCODE
} finally { Pop-Location }
