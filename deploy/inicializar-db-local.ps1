param(
    [string]$MysqlHome,
    [int]$Puerto = 3307,
    [string]$DirectorioDatos
)
$ErrorActionPreference = 'Stop'
$despliegue = $PSScriptRoot
$local = Join-Path $despliegue '.local'
$datos = if ($DirectorioDatos) { $DirectorioDatos } else { Join-Path $local 'mysql' }
$logs = Join-Path $local 'logs'
$envFile = Join-Path $local 'env.ps1'
$pidFile = Join-Path $local 'mysql.pid'
New-Item -ItemType Directory -Path $local -Force | Out-Null
New-Item -ItemType Directory -Path $logs -Force | Out-Null

if (-not $MysqlHome) {
    $candidatos = @('C:\Program Files\MySQL\MySQL Server 9.5', 'C:\Program Files\MySQL\MySQL Server 8.4', 'C:\Program Files\MariaDB\MariaDB')
    $MysqlHome = $candidatos | Where-Object { Test-Path -LiteralPath (Join-Path $_ 'bin\mysqld.exe') } | Select-Object -First 1
    if (-not $MysqlHome) { throw 'Pasa -MysqlHome con la carpeta raíz de MySQL o MariaDB.' }
}
$mysqlHome = (Resolve-Path -LiteralPath $MysqlHome).Path
$mysqld = Join-Path $mysqlHome 'bin\mysqld.exe'
$mysql = Join-Path $mysqlHome 'bin\mysql.exe'
if (-not (Test-Path -LiteralPath $mysqld) -or -not (Test-Path -LiteralPath $mysql)) { throw "No encontré mysqld.exe/mysql.exe en $mysqlHome" }

function Puerto-Abierto {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $Puerto -ErrorAction SilentlyContinue)
}

function Esperar-Mysql([object]$proceso) {
    $fin = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $fin) {
        if ($proceso.HasExited) {
            $proceso.Refresh()
            $detalle = Get-Content -LiteralPath (Join-Path $logs 'mysql.err') -Tail 20 -ErrorAction SilentlyContinue
            throw "MySQL terminó con código $($proceso.ExitCode).`n$detalle"
        }
        if (Puerto-Abierto) { return }
        Start-Sleep -Milliseconds 300
    }
    throw "MySQL no abrió el puerto $Puerto."
}

function Nueva-Clave([string]$prefijo, [int]$bytes = 18) {
    $buf = New-Object byte[] $bytes
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buf) } finally { $rng.Dispose() }
    return $prefijo + [BitConverter]::ToString($buf).Replace('-', '').ToLowerInvariant()
}

function Iniciar-Mysql {
    $argumentos = @('--no-defaults', ('"--basedir=' + $mysqlHome + '"'), ('"--datadir=' + $datos + '"'), "--port=$Puerto", '--bind-address=127.0.0.1', '--mysqlx=OFF')
    $proceso = Start-Process -FilePath $mysqld -ArgumentList $argumentos -WorkingDirectory $local -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs 'mysql.out') -RedirectStandardError (Join-Path $logs 'mysql.err')
    Esperar-Mysql $proceso
    $conexion = Get-NetTCPConnection -State Listen -LocalPort $Puerto -ErrorAction Stop
    [IO.File]::WriteAllText($pidFile, "$($conexion.OwningProcess)`r`n", [Text.UTF8Encoding]::new($false))
}

if (Test-Path -LiteralPath $envFile) {
    . $envFile
    if (Puerto-Abierto) {
        Write-Host "MySQL local ya activo en puerto $Puerto."
        return
    }
    if (-not (Test-Path -LiteralPath (Join-Path $datos 'mysql'))) { throw "Faltan datos inicializados en $datos" }
    Iniciar-Mysql
    Write-Host "MySQL local iniciado en puerto $Puerto con la configuración existente."
    return
}

if (Test-Path -LiteralPath $datos) {
    $contenido = @(Get-ChildItem -LiteralPath $datos -Force -ErrorAction SilentlyContinue)
    if ($contenido.Count -gt 0) { throw "El directorio $datos no está vacío. Bórralo o pasa -DirectorioDatos uno nuevo." }
}
New-Item -ItemType Directory -Path $datos -Force | Out-Null
& $mysqld --no-defaults "--basedir=$mysqlHome" "--datadir=$datos" --initialize-insecure 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw "No se pudo inicializar MySQL: $LASTEXITCODE" }

$pAuth = Nueva-Clave 'Db!'
$pCat = Nueva-Clave 'Db!'
$pProd = Nueva-Clave 'Db!'
$pPed = Nueva-Clave 'Db!'
$pCont = Nueva-Clave 'Db!'
$jwtBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($jwtBytes) } finally { $rng.Dispose() }
$jwt = [Convert]::ToBase64String($jwtBytes)
$cat = Nueva-Clave 'Cat!'
$adminClave = Nueva-Clave 'Admin'
$clienteClave = Nueva-Clave 'Cliente'
$vendedorClave = Nueva-Clave 'Vendedor'
$operadoraClave = Nueva-Clave 'Operadora'
$repartidorClave = Nueva-Clave 'Repartidor'

Iniciar-Mysql
$sql = @"
CREATE DATABASE IF NOT EXISTS gas_volcan_auth CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS gas_volcan_categorias CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS gas_volcan_productos CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS gas_volcan_pedidos CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS gas_volcan_contacto CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'gv_auth'@'127.0.0.1' IDENTIFIED BY '$pAuth';
CREATE USER IF NOT EXISTS 'gv_cat'@'127.0.0.1' IDENTIFIED BY '$pCat';
CREATE USER IF NOT EXISTS 'gv_prod'@'127.0.0.1' IDENTIFIED BY '$pProd';
CREATE USER IF NOT EXISTS 'gv_ped'@'127.0.0.1' IDENTIFIED BY '$pPed';
CREATE USER IF NOT EXISTS 'gv_cont'@'127.0.0.1' IDENTIFIED BY '$pCont';
GRANT ALL PRIVILEGES ON gas_volcan_auth.* TO 'gv_auth'@'127.0.0.1';
GRANT ALL PRIVILEGES ON gas_volcan_categorias.* TO 'gv_cat'@'127.0.0.1';
GRANT ALL PRIVILEGES ON gas_volcan_productos.* TO 'gv_prod'@'127.0.0.1';
GRANT ALL PRIVILEGES ON gas_volcan_pedidos.* TO 'gv_ped'@'127.0.0.1';
GRANT ALL PRIVILEGES ON gas_volcan_contacto.* TO 'gv_cont'@'127.0.0.1';
"@
& $mysql --host=127.0.0.1 --port=$Puerto --user=root --execute=$sql 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw "No se pudieron crear esquemas y usuarios: $LASTEXITCODE" }

$jdbc = "jdbc:mysql://127.0.0.1:$Puerto"
$envLineas = @(
    "`$env:JWT_SECRET='$jwt'",
    "`$env:CATALOGO_INTERNO_TOKEN='$cat'",
    "`$env:AUTH_DB_URL='$jdbc/gas_volcan_auth'",
    "`$env:AUTH_DB_USER='gv_auth'",
    "`$env:AUTH_DB_PASSWORD='$pAuth'",
    "`$env:DB_URL_CATEGORIAS='$jdbc/gas_volcan_categorias'",
    "`$env:DB_USER_CATEGORIAS='gv_cat'",
    "`$env:DB_PASSWORD_CATEGORIAS='$pCat'",
    "`$env:DB_URL_PRODUCTOS='$jdbc/gas_volcan_productos'",
    "`$env:DB_USER_PRODUCTOS='gv_prod'",
    "`$env:DB_PASSWORD_PRODUCTOS='$pProd'",
    "`$env:DB_URL_PEDIDOS='$jdbc/gas_volcan_pedidos'",
    "`$env:DB_USER_PEDIDOS='gv_ped'",
    "`$env:DB_PASSWORD_PEDIDOS='$pPed'",
    "`$env:DB_URL_CONTACTO='$jdbc/gas_volcan_contacto'",
    "`$env:DB_USER_CONTACTO='gv_cont'",
    "`$env:DB_PASSWORD_CONTACTO='$pCont'",
    "`$env:CATEGORIAS_URL='http://127.0.0.1:8082'",
    "`$env:PRODUCTOS_URL='http://127.0.0.1:8083'",
    "`$env:AUTH_URL='http://127.0.0.1:8081'",
    "`$env:PEDIDOS_URL='http://127.0.0.1:8084'",
    "`$env:AUTH_ADMIN_NAME='Administrador GasVolcan'",
    "`$env:AUTH_ADMIN_EMAIL='admin@gasvolcan.local'",
    "`$env:AUTH_ADMIN_PASSWORD='$adminClave'",
    "`$env:AUTH_BOOTSTRAP_ADMIN='true'",
    "`$env:APP_SEED_CATALOGO='true'",
    "`$env:APP_SEED_STOCK='true'",
    "`$env:DEMO_CLIENTE_PASSWORD='$clienteClave'",
    "`$env:DEMO_VENDEDOR_PASSWORD='$vendedorClave'",
    "`$env:DEMO_OPERADORA_PASSWORD='$operadoraClave'",
    "`$env:DEMO_REPARTIDOR_PASSWORD='$repartidorClave'"
)
[IO.File]::WriteAllText($envFile, (($envLineas -join "`r`n") + "`r`n"), [Text.UTF8Encoding]::new($false))
$credenciales = @(
    'Gas El Volcan - credenciales de la demo local',
    'Estas claves son solo del ensayo. No son las claves de conexion a MySQL.',
    '',
    "admin@gasvolcan.local / $adminClave",
    "cliente@gasvolcan.cl / $clienteClave",
    "vendedor@gasvolcan.cl / $vendedorClave",
    "operadora@gasvolcan.cl / $operadoraClave",
    "repartidor@gasvolcan.cl / $repartidorClave"
)
[IO.File]::WriteAllText((Join-Path $local 'credenciales.txt'), (($credenciales -join "`r`n") + "`r`n"), [Text.UTF8Encoding]::new($false))
Write-Host "MySQL local listo en puerto $Puerto con cinco esquemas."
Write-Host "Claves web en $local\credenciales.txt"
