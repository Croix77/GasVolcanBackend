param(
    [string]$MysqlHome,
    [int]$MysqlPuerto = 0,
    [string]$DirectorioDatos,
    [string]$Frontend,
    [switch]$SkipFrontend,
    [switch]$ForzarBuild,
    [switch]$MysqlExterno,
    [string]$MysqlAdminUsuario = 'root',
    [AllowEmptyString()][string]$MysqlAdminPassword = ''
)
$ErrorActionPreference = 'Stop'
$despliegue = $PSScriptRoot
$backend = Split-Path -Parent $despliegue
$local = Join-Path $despliegue '.local'
$logs = Join-Path $local 'logs'
$envFile = Join-Path $local 'env.ps1'
$iniciados = [ordered]@{}
New-Item -ItemType Directory -Path $logs -Force | Out-Null

if (-not $PSBoundParameters.ContainsKey('MysqlPuerto')) { $MysqlPuerto = if ($MysqlExterno) { 3306 } else { 3307 } }

function Puerto-Abierto([int]$puerto) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue)
}

function Proceso-En-Puerto([int]$puerto) {
    $conexion = Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $conexion) { return $null }
    $id = [int]$conexion.OwningProcess
    $instancia = Get-CimInstance Win32_Process -Filter "ProcessId = $id" -ErrorAction SilentlyContinue
    if (-not $instancia) { return $null }
    $proceso = Get-Process -Id $id -ErrorAction SilentlyContinue
    return [pscustomobject]@{
        Id          = $id
        CommandLine = [string]$instancia.CommandLine
        Inicio      = if ($proceso) { $proceso.StartTime } else { [datetime]::MinValue }
    }
}

function Esperar([int]$puerto, [object]$proceso, [string]$nombre, [string]$rutaSalud) {
    $fin = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $fin) {
        if ($proceso.HasExited) {
            $proceso.Refresh()
            $detalle = Get-Content -LiteralPath (Join-Path $logs "$nombre.err") -Tail 20 -ErrorAction SilentlyContinue
            throw "$nombre terminó con código $($proceso.ExitCode).`n$detalle"
        }
        if (Puerto-Abierto $puerto) {
            try {
                $respuesta = Invoke-WebRequest -Uri "http://127.0.0.1:$puerto$rutaSalud" -UseBasicParsing -TimeoutSec 3
                if ([int]$respuesta.StatusCode -lt 500) { return }
            } catch {
                Start-Sleep -Milliseconds 300
            }
        } else {
            Start-Sleep -Milliseconds 300
        }
    }
    throw "$nombre no respondió en http://127.0.0.1:$puerto$rutaSalud."
}

function Iniciar([string]$nombre, [int]$puerto, [string]$ejecutable, [string[]]$argumentos,
        [string]$directorio, [hashtable]$variables, [string]$marca, [string]$rutaSalud) {
    $actual = Proceso-En-Puerto $puerto
    if ($actual) {
        if ($actual.CommandLine -notlike "*$marca*") {
            throw "Puerto $puerto ocupado por el proceso $($actual.Id). No se puede iniciar $nombre."
        }
        $reiniciar = $false
        if ($nombre -ne 'vite' -and (Test-Path -LiteralPath $envFile)) {
            $cambio = (Get-Item -LiteralPath $envFile).LastWriteTime
            if ($actual.Inicio -lt $cambio) { $reiniciar = $true }
        }
        if (-not $reiniciar) {
            Write-Host "YA ACTIVO · $nombre puerto=$puerto"
            return
        }
        Stop-Process -Id $actual.Id -Force
        Start-Sleep -Milliseconds 700
        Write-Host "REINICIADO · $nombre porque cambió $envFile"
    }
    foreach ($clave in $variables.Keys) { Set-Item -Path "Env:$clave" -Value $variables[$clave] }
    $proceso = Start-Process -FilePath $ejecutable -ArgumentList $argumentos -WorkingDirectory $directorio -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs "$nombre.out") -RedirectStandardError (Join-Path $logs "$nombre.err")
    $iniciados[$nombre] = $proceso.Id
    Esperar $puerto $proceso $nombre $rutaSalud
    Write-Host "INICIADO · $nombre puerto=$puerto"
}

function Requerido([string]$clave) {
    $valor = [Environment]::GetEnvironmentVariable($clave)
    if ([string]::IsNullOrWhiteSpace($valor)) { throw "Falta $clave en $envFile. Ejecuta el script de inicialización de DB." }
    return $valor
}

$initArgs = @{ Puerto = $MysqlPuerto }
if ($MysqlHome) { $initArgs['MysqlHome'] = $MysqlHome }
if ($DirectorioDatos) { $initArgs['DirectorioDatos'] = $DirectorioDatos }
if ($MysqlExterno) {
    $initArgs['MysqlExterno'] = $true
    $initArgs['MysqlAdminUsuario'] = $MysqlAdminUsuario
    $initArgs['MysqlAdminPassword'] = $MysqlAdminPassword
}
& (Join-Path $despliegue 'inicializar-db-local.ps1') @initArgs
if (-not (Test-Path -LiteralPath $envFile)) { throw "No se generó $envFile" }
. $envFile

foreach ($clave in @('JWT_SECRET', 'AUTH_DB_URL', 'AUTH_DB_USER', 'AUTH_DB_PASSWORD',
        'DB_URL_CATEGORIAS', 'DB_USER_CATEGORIAS', 'DB_PASSWORD_CATEGORIAS',
        'DB_URL_PRODUCTOS', 'DB_USER_PRODUCTOS', 'DB_PASSWORD_PRODUCTOS',
        'DB_URL_PEDIDOS', 'DB_USER_PEDIDOS', 'DB_PASSWORD_PEDIDOS',
        'DB_URL_CONTACTO', 'DB_USER_CONTACTO', 'DB_PASSWORD_CONTACTO')) {
    Requerido $clave | Out-Null
}
$usuariosDb = @($env:AUTH_DB_USER, $env:DB_USER_CATEGORIAS, $env:DB_USER_PRODUCTOS,
        $env:DB_USER_PEDIDOS, $env:DB_USER_CONTACTO)
if ($usuariosDb -contains 'root') { throw "$envFile usa root para la app. Usa usuarios dedicados." }

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
function Marca([string]$modulo) { return Split-Path -Leaf $jars[$modulo] }
$salud = '/v3/api-docs'

Iniciar 'ms-auth' 8081 $java (& $jarArgs $jars['ms-auth']) $backend @{
    AUTH_DB_URL = $env:AUTH_DB_URL; AUTH_DB_USER = $env:AUTH_DB_USER; AUTH_DB_PASSWORD = $env:AUTH_DB_PASSWORD
} (Marca 'ms-auth') $salud
Iniciar 'ms-categorias' 8082 $java (& $jarArgs $jars['ms-categorias']) $backend @{
    DB_URL = $env:DB_URL_CATEGORIAS; DB_USER = $env:DB_USER_CATEGORIAS; DB_PASSWORD = $env:DB_PASSWORD_CATEGORIAS; JWT_SECRET = $env:JWT_SECRET; CATALOGO_INTERNO_TOKEN = $env:CATALOGO_INTERNO_TOKEN
} (Marca 'ms-categorias') $salud
Iniciar 'ms-productos' 8083 $java (& $jarArgs $jars['ms-productos']) $backend @{
    DB_URL = $env:DB_URL_PRODUCTOS; DB_USER = $env:DB_USER_PRODUCTOS; DB_PASSWORD = $env:DB_PASSWORD_PRODUCTOS; JWT_SECRET = $env:JWT_SECRET; CATALOGO_INTERNO_TOKEN = $env:CATALOGO_INTERNO_TOKEN
} (Marca 'ms-productos') $salud
Iniciar 'ms-pedidos' 8084 $java (& $jarArgs $jars['ms-pedidos']) $backend @{
    DB_URL = $env:DB_URL_PEDIDOS; DB_USER = $env:DB_USER_PEDIDOS; DB_PASSWORD = $env:DB_PASSWORD_PEDIDOS; JWT_SECRET = $env:JWT_SECRET
} (Marca 'ms-pedidos') $salud
Iniciar 'ms-despacho' 8085 $java (& $jarArgs $jars['ms-despacho']) $backend @{
    JWT_SECRET = $env:JWT_SECRET
} (Marca 'ms-despacho') $salud
Iniciar 'ms-contacto' 8086 $java (& $jarArgs $jars['ms-contacto']) $backend @{
    DB_URL = $env:DB_URL_CONTACTO; DB_USER = $env:DB_USER_CONTACTO; DB_PASSWORD = $env:DB_PASSWORD_CONTACTO; JWT_SECRET = $env:JWT_SECRET
} (Marca 'ms-contacto') $salud

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

$cliente = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8081/api/auth/login' -ContentType 'application/json' -Body (@{
    correo = 'cliente@gasvolcan.cl'; password = $env:DEMO_CLIENTE_PASSWORD
} | ConvertTo-Json -Compress)
$cuerpoContacto = @{
    nombre = 'Cliente Demo'; mensaje = 'Prueba de arranque'; referencia = [guid]::NewGuid().ToString()
} | ConvertTo-Json -Compress
$contacto = Invoke-WebRequest -Method Post -Uri 'http://127.0.0.1:8086/api/v1/contacto/mensajes' `
    -Headers @{ Authorization = "Bearer $($cliente.token)" } -ContentType 'application/json' -Body $cuerpoContacto -UseBasicParsing
if ([int]$contacto.StatusCode -ne 201) { throw "ms-contacto respondió HTTP $($contacto.StatusCode) con un JWT válido." }
Write-Host 'CONTACTO OK · login cliente + POST mensaje = 201'

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
    Iniciar 'vite' 5173 (Get-Command node).Source @(('"' + $vite + '"'), '--host', '127.0.0.1') $frontPath @{} 'vite.js' '/'
}

$lineas = foreach ($nombre in $iniciados.Keys) { "$nombre=$($iniciados[$nombre])" }
[IO.File]::WriteAllText((Join-Path $local 'pids.txt'), (($lineas -join "`r`n") + "`r`n"), [Text.UTF8Encoding]::new($false))
Write-Host 'TODO_LISTO'
Write-Host "Web: http://127.0.0.1:5173"
Write-Host "Credenciales: $local\credenciales.txt"
