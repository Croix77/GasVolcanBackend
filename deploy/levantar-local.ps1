param(
    [string]$MysqlHome,
    [int]$MysqlPuerto = 3307,
    [string]$DirectorioDatos,
    [string]$Frontend,
    [switch]$SkipFrontend,
    [switch]$ForzarBuild
)
$ErrorActionPreference = 'Stop'
$despliegue = $PSScriptRoot
$backend = Split-Path -Parent $despliegue
$local = Join-Path $despliegue '.local'
$logs = Join-Path $local 'logs'
$envFile = Join-Path $local 'env.ps1'
New-Item -ItemType Directory -Path $logs -Force | Out-Null

function Puerto-Abierto([int]$puerto) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue)
}

function Esperar([int]$puerto, [object]$proceso, [string]$nombre) {
    $fin = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $fin) {
        if ($proceso.HasExited) {
            $proceso.Refresh()
            $detalle = Get-Content -LiteralPath (Join-Path $logs "$nombre.err") -Tail 20 -ErrorAction SilentlyContinue
            throw "$nombre terminó con código $($proceso.ExitCode).`n$detalle"
        }
        if (Puerto-Abierto $puerto) { return }
        Start-Sleep -Milliseconds 300
    }
    throw "$nombre no abrió el puerto $puerto."
}

function Iniciar([string]$nombre, [int]$puerto, [string]$ejecutable, [string[]]$argumentos, [string]$directorio, [hashtable]$variables) {
    if (Puerto-Abierto $puerto) {
        Write-Host "YA ACTIVO · $nombre puerto=$puerto"
        return
    }
    foreach ($clave in $variables.Keys) { Set-Item -Path "Env:$clave" -Value $variables[$clave] }
    $proceso = Start-Process -FilePath $ejecutable -ArgumentList $argumentos -WorkingDirectory $directorio -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs "$nombre.out") -RedirectStandardError (Join-Path $logs "$nombre.err")
    Esperar $puerto $proceso $nombre
    Write-Host "INICIADO · $nombre puerto=$puerto"
}

$initArgs = @{ Puerto = $MysqlPuerto }
if ($MysqlHome) { $initArgs['MysqlHome'] = $MysqlHome }
if ($DirectorioDatos) { $initArgs['DirectorioDatos'] = $DirectorioDatos }
& (Join-Path $despliegue 'inicializar-db-local.ps1') @initArgs
if (-not (Test-Path -LiteralPath $envFile)) { throw "No se generó $envFile" }
. $envFile

$modulos = @('ms-auth', 'ms-categorias', 'ms-productos', 'ms-pedidos', 'ms-despacho', 'ms-contacto')
$jars = @{}
foreach ($modulo in $modulos) {
    $jar = Join-Path $backend "$modulo/target/$modulo-0.0.1-SNAPSHOT.jar"
    $jars[$modulo] = $jar
    if ($ForzarBuild -or -not (Test-Path -LiteralPath $jar)) {
        $mvnw = Join-Path $backend "$modulo\mvnw.cmd"
        Push-Location -LiteralPath (Join-Path $backend $modulo)
        try {
            & $mvnw -q -DskipTests package
            if ($LASTEXITCODE -ne 0) { throw "Falló la compilación de $modulo" }
        } finally {
            Pop-Location
        }
    }
}

$java = (Get-Command java).Source
$jarArgs = {
    param($jar)
    return @('-Xms64m', '-Xmx384m', '-jar', ('"' + $jar + '"'), '--server.address=127.0.0.1')
}

Iniciar 'ms-auth' 8081 $java (& $jarArgs $jars['ms-auth']) $backend @{}
Iniciar 'ms-categorias' 8082 $java (& $jarArgs $jars['ms-categorias']) $backend @{
    DB_URL = $env:DB_URL_CATEGORIAS; DB_USER = $env:DB_USER_CATEGORIAS; DB_PASSWORD = $env:DB_PASSWORD_CATEGORIAS; APP_SEED_CATALOGO = 'true'
}
Iniciar 'ms-productos' 8083 $java (& $jarArgs $jars['ms-productos']) $backend @{
    DB_URL = $env:DB_URL_PRODUCTOS; DB_USER = $env:DB_USER_PRODUCTOS; DB_PASSWORD = $env:DB_PASSWORD_PRODUCTOS; APP_SEED_CATALOGO = 'true'
}
Iniciar 'ms-pedidos' 8084 $java (& $jarArgs $jars['ms-pedidos']) $backend @{
    DB_URL = $env:DB_URL_PEDIDOS; DB_USER = $env:DB_USER_PEDIDOS; DB_PASSWORD = $env:DB_PASSWORD_PEDIDOS; APP_SEED_STOCK = 'true'
}
Iniciar 'ms-despacho' 8085 $java (& $jarArgs $jars['ms-despacho']) $backend @{}
Iniciar 'ms-contacto' 8086 $java (& $jarArgs $jars['ms-contacto']) $backend @{
    DB_URL = $env:DB_URL_CONTACTO; DB_USER = $env:DB_USER_CONTACTO; DB_PASSWORD = $env:DB_PASSWORD_CONTACTO
}

$login = @{
    correo = $env:AUTH_ADMIN_EMAIL
    password = $env:AUTH_ADMIN_PASSWORD
} | ConvertTo-Json -Compress
$admin = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8081/api/auth/login' -ContentType 'application/json' -Body $login
$cabeceras = @{ Authorization = "Bearer $($admin.token)" }
$actuales = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/auth/usuarios' -Headers $cabeceras
$demo = @(
    @{ nombre = 'Cliente Demo'; correo = 'cliente@gasvolcan.cl'; rol = 'CLIENTE'; password = $env:DEMO_CLIENTE_PASSWORD },
    @{ nombre = 'Vendedor Demo'; correo = 'vendedor@gasvolcan.cl'; rol = 'VENDEDOR'; password = $env:DEMO_VENDEDOR_PASSWORD },
    @{ nombre = 'Operadora Demo'; correo = 'operadora@gasvolcan.cl'; rol = 'OPERADORA'; password = $env:DEMO_OPERADORA_PASSWORD },
    @{ nombre = 'Repartidor Demo'; correo = 'repartidor@gasvolcan.cl'; rol = 'REPARTIDOR'; password = $env:DEMO_REPARTIDOR_PASSWORD }
)
foreach ($cuenta in $demo) {
    if ($actuales.correo -contains $cuenta.correo) { continue }
    $null = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8081/api/auth/usuarios' -Headers $cabeceras -ContentType 'application/json' -Body ($cuenta | ConvertTo-Json -Compress)
    Write-Host "CREADO · $($cuenta.correo)"
}

if (-not $SkipFrontend) {
    if (-not $Frontend) { $Frontend = Join-Path (Split-Path -Parent $backend) 'GasvolcanReact' }
    $frontPath = (Resolve-Path -LiteralPath $Frontend -ErrorAction SilentlyContinue).Path
    if (-not $frontPath) { throw "No encontré React en $Frontend. Pasa -Frontend o -SkipFrontend." }
    if (-not (Test-Path -LiteralPath (Join-Path $frontPath 'node_modules'))) {
        & npm.cmd ci --prefix $frontPath
        if ($LASTEXITCODE -ne 0) { throw 'Falló npm ci en el frontend.' }
    }
    $vite = Join-Path $frontPath 'node_modules\vite\bin\vite.js'
    if (-not (Test-Path -LiteralPath $vite)) { throw "Falta Vite en $vite" }
    Iniciar 'vite' 5173 (Get-Command node).Source @(('"' + $vite + '"'), '--host', '127.0.0.1') $frontPath @{}
}

$puertos = [ordered]@{
    'mysql-local' = $MysqlPuerto; 'ms-auth' = 8081; 'ms-categorias' = 8082; 'ms-productos' = 8083
    'ms-pedidos' = 8084; 'ms-despacho' = 8085; 'ms-contacto' = 8086; 'vite' = 5173
}
$lineas = foreach ($nombre in $puertos.Keys) {
    if (Puerto-Abierto $puertos[$nombre]) {
        $conexion = Get-NetTCPConnection -State Listen -LocalPort $puertos[$nombre] -ErrorAction Stop
        "$nombre=$($conexion.OwningProcess)"
    }
}
[IO.File]::WriteAllText((Join-Path $local 'pids.txt'), (($lineas -join "`r`n") + "`r`n"), [Text.UTF8Encoding]::new($false))
Write-Host 'TODO_LISTO'
Write-Host "Web: http://127.0.0.1:5173"
Write-Host "Credenciales: $local\credenciales.txt"
