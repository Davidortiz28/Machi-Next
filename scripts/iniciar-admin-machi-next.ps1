$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$demoEnv = Join-Path $repoRoot '.env.demo.local'
$serverScript = Join-Path $PSScriptRoot 'iniciar-machi-next.ps1'
$baseUrl = 'http://localhost:8081'

if (!(Test-Path $demoEnv)) {
    throw 'Falta .env.demo.local. Define ADMIN_EMAIL y ADMIN_PASSWORD con una cuenta de demostración activa con rol ADMIN.'
}

$settings = @{}
foreach ($line in Get-Content $demoEnv) {
    if ($line -match '^\s*([^#\s][^=]*)=(.*)$') {
        $name = $Matches[1].Trim()
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $settings[$name] = $value
    }
}
if (!$settings.ADMIN_EMAIL -or !$settings.ADMIN_PASSWORD) {
    throw 'Configura ADMIN_EMAIL y ADMIN_PASSWORD en .env.demo.local.'
}

Write-Host 'Iniciando Machi Next para administración...'
& $serverScript -NoBrowser
if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { throw 'No fue posible iniciar Machi Next.' }

$loginBody = @{ email = $settings.ADMIN_EMAIL; password = $settings.ADMIN_PASSWORD } | ConvertTo-Json
try {
    $launch = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/auth/admin-launch" `
        -ContentType 'application/json' -Body $loginBody -TimeoutSec 10
} catch {
    throw 'No se pudo validar la cuenta administrativa. Comprueba las credenciales locales de demostración.'
}
if (!$launch.code) { throw 'El servidor no generó un código de acceso administrativo.' }

Write-Host 'Abriendo el panel de administración...'
Start-Process "$baseUrl/admin-launch.html#code=$($launch.code)"
