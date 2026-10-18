$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
$venv = Join-Path $project '.venv'
$compose = Join-Path $venv 'Scripts\podman-compose.exe'

if (-not (Test-Path -LiteralPath (Join-Path $project '.env'))) {
    throw 'Missing .env. Run .\prepare-podman-env.ps1 first.'
}

if (-not (Test-Path -LiteralPath $compose)) {
    py -m venv $venv
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the Python environment.' }
    & (Join-Path $venv 'Scripts\python.exe') -m pip install 'podman-compose==1.6.0'
    if ($LASTEXITCODE -ne 0) { throw 'Could not install podman-compose.' }
}

$podman = Get-Command podman -ErrorAction SilentlyContinue
if ($podman) {
    $podmanExe = $podman.Source
    $env:PATH = (Split-Path $podmanExe -Parent) + ';' + $env:PATH
} else {
    $candidate = Join-Path $env:LOCALAPPDATA 'Programs\Podman\podman.exe'
    if (-not (Test-Path -LiteralPath $candidate)) { throw 'Podman CLI was not found.' }
    $podmanExe = $candidate
    $env:PATH = (Split-Path $candidate -Parent) + ';' + $env:PATH
}

Push-Location $project
try {
    $composeArgs = @($args)
    if ($composeArgs -contains '--build') {
        $registryLine = Get-Content -LiteralPath (Join-Path $project '.env') | Where-Object { $_ -match '^IMAGE_REGISTRY=' } | Select-Object -Last 1
        $tagLine = Get-Content -LiteralPath (Join-Path $project '.env') | Where-Object { $_ -match '^IMAGE_TAG=' } | Select-Object -Last 1
        if ($registryLine -ne 'IMAGE_REGISTRY=localhost' -or $tagLine -ne 'IMAGE_TAG=dev') {
            throw 'Set IMAGE_REGISTRY=localhost and IMAGE_TAG=dev in .env before a local build.'
        }
        $services = [ordered]@{
            'eureka-server' = 'eureka'
            'o-back-identity' = 'identity'
            'o-back-banking' = 'banking'
            'o-bank-credit' = 'credit'
            'o-bank-forex' = 'forex'
            'o-bank-investment' = 'investment'
            'o-back-gateway' = 'gateway'
        }
        foreach ($service in $services.Keys) {
            $image = "localhost/o-bank-$($services[$service]):dev"
            & $podmanExe build --pull=never -f Containerfile.service --build-arg "SERVICE=$service" -t $image .
            if ($LASTEXITCODE -ne 0) { throw "Podman build failed for $service" }
        }
        $frontend = [IO.Path]::GetFullPath((Join-Path $project 'frontend'))
        if (-not (Test-Path -LiteralPath (Join-Path $frontend 'Containerfile.mirrored'))) { throw 'Mirrored frontend Containerfile is missing.' }
        & $podmanExe build --pull=never -f (Join-Path $frontend 'Containerfile.mirrored') -t localhost/o-bank-frontend:dev $frontend
        if ($LASTEXITCODE -ne 0) { throw 'Podman build failed for the frontend.' }
        $composeArgs = @($composeArgs | Where-Object { $_ -ne '--build' })
        if (($composeArgs -contains 'up') -and -not ($composeArgs -contains '--force-recreate')) {
            $composeArgs += '--force-recreate'
        }
    }
    & $compose --env-file .env -f compose.mirrored.yaml @composeArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
