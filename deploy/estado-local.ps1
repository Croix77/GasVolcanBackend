param([int]$MysqlPuerto = 0)
$ErrorActionPreference = 'Stop'
$despliegue = $PSScriptRoot
$local = Join-Path $despliegue '.local'
$envFile = Join-Path $local 'env.ps1'
if (Test-Path -LiteralPath $envFile) { . $envFile }

function Puerto-De-Jdbc([string]$url) {
    if ($url -match '(?i)mysql://[^:/]+:(\d+)/') { return [int]$Matches[1] }
    return 3306
}
function Proceso-En-Puerto([int]$puerto) {
    $conexion = Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $conexion) { return $null }
    $id = [int]$conexion.OwningProcess
    $instancia = Get-CimInstance Win32_Process -Filter "ProcessId = $id" -ErrorAction SilentlyContinue
    if (-not $instancia) { return [pscustomobject]@{ Id = $id; CommandLine = '' } }
    return [pscustomobject]@{ Id = $id; CommandLine = [string]$instancia.CommandLine }
}
function Responde([int]$puerto, [string]$ruta) {
    try {
        $respuesta = Invoke-WebRequest -Uri "http://127.0.0.1:$puerto$ruta" -UseBasicParsing -TimeoutSec 4
        return [int]$respuesta.StatusCode -lt 500
    } catch { return $false }
}
function Estado([string]$nombre, [int]$puerto, [string]$marca, [string]$ruta) {
    $proceso = Proceso-En-Puerto $puerto
    if (-not $proceso) { Write-Host "FALTA · $nombre puerto=$puerto"; return $false }
    if ($marca -and $proceso.CommandLine -notlike "*$marca*") {
        Write-Host "FALLO · $nombre puerto=$puerto ocupado por pid=$($proceso.Id)"; return $false
    }
    if (-not (Responde $puerto $ruta)) { Write-Host "FALLO · $nombre puerto=$puerto no responde"; return $false }
    Write-Host "OK · $nombre puerto=$puerto pid=$($proceso.Id)"; return $true
}

if ($MysqlPuerto -eq 0) { $MysqlPuerto = Puerto-De-Jdbc $env:AUTH_DB_URL }
$modo = if ($env:GV_MYSQL_MODO) { $env:GV_MYSQL_MODO } else { 'sin-registrar' }
"MYSQL · puerto=$MysqlPuerto modo=$modo"
if (-not (Proceso-En-Puerto $MysqlPuerto)) { "FALTA · mysql puerto=$MysqlPuerto" }
else { "OK · mysql puerto=$MysqlPuerto pid=$((Proceso-En-Puerto $MysqlPuerto).Id)" }

$servicios = @(
    @{ nombre = 'ms-auth'; puerto = 8081; marca = 'ms-auth-0.0.1-SNAPSHOT.jar' },
    @{ nombre = 'ms-categorias'; puerto = 8082; marca = 'ms-categorias-0.0.1-SNAPSHOT.jar' },
    @{ nombre = 'ms-productos'; puerto = 8083; marca = 'ms-productos-0.0.1-SNAPSHOT.jar' },
    @{ nombre = 'ms-pedidos'; puerto = 8084; marca = 'ms-pedidos-0.0.1-SNAPSHOT.jar' },
    @{ nombre = 'ms-despacho'; puerto = 8085; marca = 'ms-despacho-0.0.1-SNAPSHOT.jar' },
    @{ nombre = 'ms-contacto'; puerto = 8086; marca = 'ms-contacto-0.0.1-SNAPSHOT.jar' },
    @{ nombre = 'vite'; puerto = 5173; marca = 'vite.js' }
)
$activos = @{}
foreach ($servicio in $servicios) {
    $ruta = if ($servicio.nombre -eq 'vite') { '/' } else { '/v3/api-docs' }
    $activos[$servicio.nombre] = Estado $servicio.nombre $servicio.puerto $servicio.marca $ruta
}

$authBase = 'http://127.0.0.1:8081'
$credenciales = @(
    @{ correo = $env:AUTH_ADMIN_EMAIL; password = $env:AUTH_ADMIN_PASSWORD },
    @{ correo = 'cliente@gasvolcan.cl'; password = $env:DEMO_CLIENTE_PASSWORD },
    @{ correo = 'vendedor@gasvolcan.cl'; password = $env:DEMO_VENDEDOR_PASSWORD },
    @{ correo = 'operadora@gasvolcan.cl'; password = $env:DEMO_OPERADORA_PASSWORD },
    @{ correo = 'repartidor@gasvolcan.cl'; password = $env:DEMO_REPARTIDOR_PASSWORD }
)
$tokenCliente = $null
foreach ($credencial in $credenciales) {
    try {
        $respuesta = Invoke-RestMethod -Method Post -Uri "$authBase/api/auth/login" -ContentType 'application/json' -Body ($credencial | ConvertTo-Json -Compress)
        "LOGIN OK · $($credencial.correo) rol=$($respuesta.rol)"
        if ($credencial.correo -eq 'cliente@gasvolcan.cl') { $tokenCliente = $respuesta.token }
    } catch {
        "LOGIN FALLO · $($credencial.correo)"
    }
}

if ($tokenCliente -and $activos['ms-contacto']) {
    $cuerpo = @{
        nombre = 'Cliente Demo'; mensaje = 'Prueba de estado'; referencia = [guid]::NewGuid().ToString()
    } | ConvertTo-Json -Compress
    try {
        $respuesta = Invoke-WebRequest -Method Post -Uri 'http://127.0.0.1:8086/api/v1/contacto/mensajes' `
            -Headers @{ Authorization = "Bearer $tokenCliente" } -ContentType 'application/json' -Body $cuerpo -UseBasicParsing
        if ([int]$respuesta.StatusCode -eq 201) { 'CONTACTO OK · login cliente + POST mensaje = 201' }
        else { "CONTACTO FALLO · HTTP $($respuesta.StatusCode)" }
    } catch {
        "CONTACTO FALLO · $($_.Exception.Message)"
    }
} else {
    'CONTACTO OMITIDO · ms-contacto o login de cliente no disponible'
}
