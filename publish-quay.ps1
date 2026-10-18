param(
    [Parameter(Mandatory = $true)][string]$Namespace,
    [string]$Tag = 'dev'
)

$ErrorActionPreference = 'Stop'
if ($Namespace -notmatch '^[a-z0-9][a-z0-9._-]*$') { throw 'Enter your exact Quay username or organization namespace.' }
if ($Tag -notmatch '^[A-Za-z0-9_][A-Za-z0-9_.-]*$') { throw 'Invalid image tag.' }
$podmanCommand = Get-Command podman -ErrorAction SilentlyContinue
if (-not $podmanCommand) {
    $candidate = Join-Path $env:LOCALAPPDATA 'Programs\Podman\podman.exe'
    if (-not (Test-Path -LiteralPath $candidate)) { throw 'Podman CLI was not found.' }
    $env:PATH = (Split-Path $candidate -Parent) + ';' + $env:PATH
}

$images = @('eureka', 'identity', 'banking', 'credit', 'forex', 'investment', 'gateway', 'frontend')
foreach ($name in $images) {
    $localImage = "localhost/o-bank-${name}:dev"
    & podman image exists $localImage
    if ($LASTEXITCODE -ne 0) { throw "Build $localImage with .\run-mirrored.ps1 up -d --build first." }
}

& podman login quay.io
if ($LASTEXITCODE -ne 0) { throw 'Quay login failed.' }

foreach ($name in $images) {
    $localImage = "localhost/o-bank-${name}:dev"
    $quayImage = "quay.io/$Namespace/o-bank-${name}:$Tag"
    & podman tag $localImage $quayImage
    if ($LASTEXITCODE -ne 0) { throw "Could not tag $localImage" }
    & podman push $quayImage
    if ($LASTEXITCODE -ne 0) { throw "Could not push $quayImage" }
    Write-Output "Published $quayImage"
}
