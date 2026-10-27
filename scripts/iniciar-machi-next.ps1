param([switch]$NoBrowser)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backendDir = Join-Path $repoRoot 'backend'
$jarPath = Join-Path $backendDir 'target\machi-next-api-0.1.0-SNAPSHOT.jar'
$envPath = Join-Path $repoRoot '.env'
$stdoutPath = Join-Path $env:TEMP 'machi-next-app.log'
$stderrPath = Join-Path $env:TEMP 'machi-next-app-error.log'
$appUrl = 'http://localhost:8081'
$healthUrl = "$appUrl/actuator/health"

function Test-AppHealth {
    try {
        $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2
        return $health.status -eq 'UP'
    } catch {
        return $false
    }
}

function Test-DockerEngine {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    docker info *> $null
    $available = $LASTEXITCODE -eq 0
    $ErrorActionPreference = $previousPreference
    return $available
}

Write-Host 'Iniciando Machi Next...'

$latestSource = Get-ChildItem (Join-Path $backendDir 'src\main') -Recurse -File |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
$sourceIsNewer = $latestSource -and (!(Test-Path $jarPath) -or $latestSource.LastWriteTime -gt (Get-Item $jarPath).LastWriteTime)

if ((Test-AppHealth) -and (-not $sourceIsNewer)) {
    Write-Host 'El backend ya estaba iniciado y actualizado.'
    if (!$NoBrowser) { Start-Process $appUrl }
    exit 0
}

if ((Test-AppHealth) -and $sourceIsNewer) {
    Write-Host 'Hay cambios nuevos. Reiniciando el backend para aplicarlos...'
    $listener = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) {
        $process = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
        if ($process -and $process.ProcessName -eq 'java') {
            Stop-Process -Id $process.Id -Force
            for ($attempt = 0; $attempt -lt 30 -and (Test-AppHealth); $attempt++) { Start-Sleep -Seconds 1 }
        } else {
            throw 'El puerto 8081 lo ocupa otro programa. Ciérralo antes de iniciar Machi Next.'
        }
    }
}

if (!(Test-DockerEngine)) {
    $desktopCandidates = @(
        (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\Docker Desktop.exe'),
        (Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe')
    )
    $desktopPath = $desktopCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (!$desktopPath) {
        throw 'No encuentro Docker Desktop. Instalala o abrelo manualmente y vuelve a ejecutar este archivo.'
    }
    Write-Host 'Abriendo Docker Desktop y esperando al motor...'
    Start-Process -FilePath $desktopPath
    $dockerReady = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        Start-Sleep -Seconds 2
        if (Test-DockerEngine) { $dockerReady = $true; break }
    }
    if (!$dockerReady) {
        throw 'Docker no inicio su motor. Abre Docker Desktop, espera a que diga que esta iniciado y vuelve a ejecutar este archivo.'
    }
}

if (!(Test-Path $envPath)) {
    throw 'Falta el archivo .env en la raiz del proyecto. Sigue las instrucciones de database\README.md para crearlo.'
}

Write-Host 'Iniciando la base de datos...'
docker compose --project-directory $repoRoot up -d db
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose no pudo iniciar MySQL.' }

$databaseReady = $false
for ($attempt = 0; $attempt -lt 90; $attempt++) {
    $containerHealth = docker inspect --format '{{.State.Health.Status}}' machi-next-mysql 2>$null
    if ($LASTEXITCODE -eq 0 -and $containerHealth.Trim() -eq 'healthy') {
        $databaseReady = $true
        break
    }
    Start-Sleep -Seconds 2
}
if (!$databaseReady) { throw 'MySQL no alcanzo el estado healthy. Revisa Docker Desktop y vuelve a intentarlo.' }

foreach ($line in Get-Content $envPath) {
    if ($line -match '^\s*([^#][^=]*)=(.*)$') {
        $name = $Matches[1].Trim()
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}
$env:DB_PORT = $env:MYSQL_PORT
$env:SERVER_PORT = '8081'

if ($sourceIsNewer) {

    Write-Host 'Compilando el backend...'
    Push-Location $backendDir
    try {
        mvn -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Maven no pudo compilar el backend.' }
    } finally {
        Pop-Location
    }
}

Write-Host 'Iniciando el backend...'
$null = Start-Process -FilePath 'java' `
    -ArgumentList @('-jar', $jarPath, '--server.port=8081', '--logging.level.root=INFO') `
    -WorkingDirectory $backendDir -WindowStyle Hidden `
    -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru

$appReady = $false
for ($attempt = 0; $attempt -lt 90; $attempt++) {
    if (Test-AppHealth) { $appReady = $true; break }
    Start-Sleep -Seconds 2
}
if (!$appReady) {
    if (Test-Path $stderrPath) { Get-Content $stderrPath -Tail 25 }
    throw "El backend no respondio. Revisa $stderrPath y $stdoutPath."
}

Write-Host 'Machi Next esta listo. Abriendo el navegador...'
if (!$NoBrowser) { Start-Process $appUrl }
