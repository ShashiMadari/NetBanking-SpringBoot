param(
    [Parameter(Mandatory=$true)][string]$Customer1Email,
    [Parameter(Mandatory=$true)][string]$Customer2Email,
    [Parameter(Mandatory=$true)][string]$AdminEmail,
    [string]$Customer1Name = 'Demo Customer One',
    [string]$Customer2Name = 'Demo Customer Two',
    [string]$AdminName = 'Demo Bank Administrator',
    [string]$Customer1Phone = '9000000101',
    [string]$Customer2Phone = '9000000102',
    [string]$AdminPhone = '9000000103',
    [string]$OracleContainer = 'o-bank-full_oracle_1',
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$privateRoot = Join-Path $root '.local-demo-seed'
$python = Join-Path $root '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $python)) {
    py -m venv (Join-Path $root '.venv')
    if ($LASTEXITCODE -ne 0) { throw 'Python environment creation failed.' }
}
& $python -c 'import importlib.util, sys; sys.exit(0 if importlib.util.find_spec(sys.argv[1]) else 1)' bcrypt
if ($LASTEXITCODE -ne 0) {
    & $python -m pip install 'bcrypt==4.3.0'
    if ($LASTEXITCODE -ne 0) { throw 'Could not install the BCrypt dependency.' }
}
$podman = Get-Command podman -ErrorAction SilentlyContinue
if ($podman) { $podmanPath=$podman.Source } else {
    $podmanPath=Join-Path $env:LOCALAPPDATA 'Programs\Podman\podman.exe'
}
if (-not $DryRun) {
    if (-not (Test-Path -LiteralPath $podmanPath)) { throw 'Podman was not found.' }
    $check = @'
whenever sqlerror exit failure rollback
set pages 0 feedback off heading off
alter session set container=FREEPDB1;
declare n number;
begin
  select count(*) into n from o_bank_identity.users;
  if n > 0 then raise_application_error(-20001,'Demo seeding requires a fresh database with no users. Existing records were preserved.'); end if;
end;
/
exit
'@
    $result = $check | & $podmanPath exec -i $OracleContainer sqlplus -s / as sysdba 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
}
New-Item -ItemType Directory -Path $privateRoot -Force | Out-Null
$id = [Guid]::NewGuid().ToString('N')
$configPath = Join-Path $privateRoot "config-$id.json"
$outputPath = Join-Path $privateRoot $id
$config = @{users=@(
    @{name=$Customer1Name;email=$Customer1Email;phone=$Customer1Phone},
    @{name=$Customer2Name;email=$Customer2Email;phone=$Customer2Phone},
    @{name=$AdminName;email=$AdminEmail;phone=$AdminPhone}
)}
$config | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $configPath -Encoding UTF8
try {
    & $python (Join-Path $root 'infrastructure\oracle\demo\prepare_seed.py') $configPath (Join-Path $root 'infrastructure\oracle\demo\seed-template.sql') $outputPath
    if ($LASTEXITCODE -ne 0) { throw 'Demo SQL preparation failed.' }
    if ($DryRun) { Write-Output "Dry run only: no database changes. Generated files: $outputPath"; return }
    $remotePath="/tmp/obank-demo-$id.sql"
    & $podmanPath cp (Join-Path $outputPath 'seed.sql') "${OracleContainer}:$remotePath"
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy the seed SQL into Oracle.' }
    try {
        & $podmanPath exec $OracleContainer sqlplus -s / as sysdba "@$remotePath"
        if ($LASTEXITCODE -ne 0) { throw 'Demo data setup failed; the SQL transaction was rolled back.' }
    } finally {
        & $podmanPath exec $OracleContainer rm -f $remotePath | Out-Null
    }
    Write-Output "Demo data added. Private passwords and card PIN: $(Join-Path $outputPath 'login-details.txt')"
    Write-Output 'Open http://localhost:8001 and use Sign in. Email OTP is required.'
} finally {
    Remove-Item -LiteralPath $configPath -Force -ErrorAction SilentlyContinue
}
