param(
    [string]$MysqlHome,
    [int]$Puerto = 3307,
    [string]$DirectorioDatos,
    [switch]$MysqlExterno,
    [string]$MysqlAdminUsuario = 'root',
    [AllowEmptyString()][string]$MysqlAdminPassword = ''
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

function Puerto-Abierto {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $Puerto -ErrorAction SilentlyContinue)
}

function Bytes-Aleatorios([int]$cantidad) {
    $buf = New-Object byte[] $cantidad
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buf) } finally { $rng.Dispose() }
    return $buf
}

function Nueva-Clave([string]$prefijo, [int]$bytes = 18) {
    $buf = Bytes-Aleatorios $bytes
    return $prefijo + [BitConverter]::ToString($buf).Replace('-', '').ToLowerInvariant()
}

function Nuevo-Secreto {
    return [Convert]::ToBase64String((Bytes-Aleatorios 32))
}

function Cliente-Mysql {
    if ($MysqlHome) {
        $propio = Join-Path $MysqlHome 'bin\mysql.exe'
        if (Test-Path -LiteralPath $propio) { return (Resolve-Path -LiteralPath $propio).Path }
    }
    $comando = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($comando) { return $comando.Source }
    $candidatos = @(
        'C:\Program Files\MySQL\MySQL Server 9.5\bin\mysql.exe',
        'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
        'C:\Program Files\MariaDB\MariaDB\bin\mysql.exe'
    )
    foreach ($candidato in $candidatos) {
        if (Test-Path -LiteralPath $candidato) { return $candidato }
    }
    $laragon = Get-ChildItem 'C:\laragon\bin\mysql' -Filter mysql.exe -Recurse -ErrorAction SilentlyContinue |
            Select-Object -First 1 -ExpandProperty FullName
    if ($laragon) { return $laragon }
    throw 'No encontré mysql.exe. Pasa -MysqlHome con la raíz de MySQL/MariaDB o agrega mysql.exe al PATH.'
}

function Ejecutar-Sql([string]$cliente, [string]$sql, [string]$usuario, [string]$clave) {
    $argumentos = @('--no-defaults', '--protocol=TCP', '--host=127.0.0.1', "--port=$Puerto",
            "--user=$usuario", "--execute=$sql")
    $anterior = $env:MYSQL_PWD
    $env:MYSQL_PWD = $clave
    try {
        & $cliente @argumentos 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "MySQL rechazó la configuración en el puerto $Puerto con el usuario '$usuario' (código $LASTEXITCODE)."
        }
    } finally {
        if ($null -eq $anterior) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
        else { $env:MYSQL_PWD = $anterior }
    }
}

function Escribir-Env([string]$jdbc, [hashtable]$passwords, [string]$modo) {
    $jwt = if ($env:JWT_SECRET) { $env:JWT_SECRET } else { Nuevo-Secreto }
    $catalogo = if ($env:CATALOGO_INTERNO_TOKEN) { $env:CATALOGO_INTERNO_TOKEN } else { Nueva-Clave 'Cat!' }
    $adminClave = if ($env:AUTH_ADMIN_PASSWORD) { $env:AUTH_ADMIN_PASSWORD } else { Nueva-Clave 'Admin' }
    $clienteClave = if ($env:DEMO_CLIENTE_PASSWORD) { $env:DEMO_CLIENTE_PASSWORD } else { Nueva-Clave 'Cliente' }
    $vendedorClave = if ($env:DEMO_VENDEDOR_PASSWORD) { $env:DEMO_VENDEDOR_PASSWORD } else { Nueva-Clave 'Vendedor' }
    $operadoraClave = if ($env:DEMO_OPERADORA_PASSWORD) { $env:DEMO_OPERADORA_PASSWORD } else { Nueva-Clave 'Operadora' }
    $repartidorClave = if ($env:DEMO_REPARTIDOR_PASSWORD) { $env:DEMO_REPARTIDOR_PASSWORD } else { Nueva-Clave 'Repartidor' }

    $envLineas = @(
        "`$env:GV_MYSQL_MODO='$modo'",
        "`$env:GV_MYSQL_PUERTO='$Puerto'",
        "`$env:JWT_SECRET='$jwt'",
        "`$env:CATALOGO_INTERNO_TOKEN='$catalogo'",
        "`$env:AUTH_DB_URL='$jdbc/gas_volcan_auth'",
        "`$env:AUTH_DB_USER='gv_auth'",
        "`$env:AUTH_DB_PASSWORD='$($passwords.auth)'",
        "`$env:DB_URL_CATEGORIAS='$jdbc/gas_volcan_categorias'",
        "`$env:DB_USER_CATEGORIAS='gv_cat'",
        "`$env:DB_PASSWORD_CATEGORIAS='$($passwords.cat)'",
        "`$env:DB_URL_PRODUCTOS='$jdbc/gas_volcan_productos'",
        "`$env:DB_USER_PRODUCTOS='gv_prod'",
        "`$env:DB_PASSWORD_PRODUCTOS='$($passwords.prod)'",
        "`$env:DB_URL_PEDIDOS='$jdbc/gas_volcan_pedidos'",
        "`$env:DB_USER_PEDIDOS='gv_ped'",
        "`$env:DB_PASSWORD_PEDIDOS='$($passwords.ped)'",
        "`$env:DB_URL_CONTACTO='$jdbc/gas_volcan_contacto'",
        "`$env:DB_USER_CONTACTO='gv_cont'",
        "`$env:DB_PASSWORD_CONTACTO='$($passwords.cont)'",
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
}

function Sql-De-Esquemas([hashtable]$passwords) {
    return @"
CREATE DATABASE IF NOT EXISTS gas_volcan_auth CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS gas_volcan_categorias CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS gas_volcan_productos CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS gas_volcan_pedidos CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS gas_volcan_contacto CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'gv_auth'@'127.0.0.1' IDENTIFIED BY '$($passwords.auth)';
CREATE USER IF NOT EXISTS 'gv_cat'@'127.0.0.1' IDENTIFIED BY '$($passwords.cat)';
CREATE USER IF NOT EXISTS 'gv_prod'@'127.0.0.1' IDENTIFIED BY '$($passwords.prod)';
CREATE USER IF NOT EXISTS 'gv_ped'@'127.0.0.1' IDENTIFIED BY '$($passwords.ped)';
CREATE USER IF NOT EXISTS 'gv_cont'@'127.0.0.1' IDENTIFIED BY '$($passwords.cont)';
ALTER USER 'gv_auth'@'127.0.0.1' IDENTIFIED BY '$($passwords.auth)';
ALTER USER 'gv_cat'@'127.0.0.1' IDENTIFIED BY '$($passwords.cat)';
ALTER USER 'gv_prod'@'127.0.0.1' IDENTIFIED BY '$($passwords.prod)';
ALTER USER 'gv_ped'@'127.0.0.1' IDENTIFIED BY '$($passwords.ped)';
ALTER USER 'gv_cont'@'127.0.0.1' IDENTIFIED BY '$($passwords.cont)';
GRANT ALL PRIVILEGES ON gas_volcan_auth.* TO 'gv_auth'@'127.0.0.1';
GRANT ALL PRIVILEGES ON gas_volcan_categorias.* TO 'gv_cat'@'127.0.0.1';
GRANT ALL PRIVILEGES ON gas_volcan_productos.* TO 'gv_prod'@'127.0.0.1';
GRANT ALL PRIVILEGES ON gas_volcan_pedidos.* TO 'gv_ped'@'127.0.0.1';
GRANT ALL PRIVILEGES ON gas_volcan_contacto.* TO 'gv_cont'@'127.0.0.1';
FLUSH PRIVILEGES;
"@
}

function Env-Valido([string]$jdbc) {
    if (-not (Test-Path -LiteralPath $envFile)) { return $false }
    . $envFile
    $urls = @($env:AUTH_DB_URL, $env:DB_URL_CATEGORIAS, $env:DB_URL_PRODUCTOS, $env:DB_URL_PEDIDOS, $env:DB_URL_CONTACTO)
    if ($urls | Where-Object { $_ -notlike "$jdbc/*" }) { return $false }
    $usuarios = @($env:AUTH_DB_USER, $env:DB_USER_CATEGORIAS, $env:DB_USER_PRODUCTOS, $env:DB_USER_PEDIDOS, $env:DB_USER_CONTACTO)
    $claves = @($env:AUTH_DB_PASSWORD, $env:DB_PASSWORD_CATEGORIAS, $env:DB_PASSWORD_PRODUCTOS, $env:DB_PASSWORD_PEDIDOS, $env:DB_PASSWORD_CONTACTO)
    if ($usuarios | Where-Object { [string]::IsNullOrWhiteSpace($_) -or $_ -eq 'root' }) { return $false }
    if ($claves | Where-Object { [string]::IsNullOrWhiteSpace($_) }) { return $false }
    return $true
}

$jdbc = "jdbc:mysql://127.0.0.1:$Puerto"

if (-not $MysqlExterno -and -not $MysqlHome) {
    $candidatos = @('C:\Program Files\MySQL\MySQL Server 9.5', 'C:\Program Files\MySQL\MySQL Server 8.4', 'C:\Program Files\MariaDB\MariaDB')
    $MysqlHome = $candidatos | Where-Object { Test-Path -LiteralPath (Join-Path $_ 'bin\mysqld.exe') } | Select-Object -First 1
    if (-not $MysqlHome) { throw 'Pasa -MysqlHome con la carpeta raíz de MySQL o MariaDB.' }
}
if ($MysqlHome) { $MysqlHome = (Resolve-Path -LiteralPath $MysqlHome).Path }
$clienteLocal = if ($MysqlHome) { Join-Path $MysqlHome 'bin\mysql.exe' } else { $null }

if ($MysqlExterno) {
    if (-not (Puerto-Abierto)) {
        throw "No hay MySQL escuchando en 127.0.0.1:$Puerto. Inicia tu MySQL/Laragon o cambia -MysqlPuerto."
    }
    $cliente = Cliente-Mysql
    if (Env-Valido $jdbc) {
        try {
            Ejecutar-Sql $cliente 'SELECT 1;' $env:AUTH_DB_USER $env:AUTH_DB_PASSWORD
            Write-Host "MySQL externo ya configurado en puerto $Puerto con usuarios dedicados."
            return
        } catch {
            Write-Host "Las credenciales guardadas no sirven; se renuevan los usuarios de la app."
        }
    }
    $passwords = @{
        auth = Nueva-Clave 'Db!'; cat = Nueva-Clave 'Db!'; prod = Nueva-Clave 'Db!'
        ped = Nueva-Clave 'Db!'; cont = Nueva-Clave 'Db!'
    }
    Ejecutar-Sql $cliente (Sql-De-Esquemas $passwords) $MysqlAdminUsuario $MysqlAdminPassword
    Escribir-Env $jdbc $passwords 'externo'
    Write-Host "MySQL externo configurado en puerto $Puerto con cinco esquemas y usuarios dedicados."
    Write-Host "Claves web en $local\credenciales.txt"
    return
}

if (Test-Path -LiteralPath $envFile) {
    . $envFile
    if (Puerto-Abierto) {
        if (Env-Valido $jdbc) {
            Write-Host "MySQL local ya activo en puerto $Puerto con usuarios dedicados."
            return
        }
        Write-Host "La configuración local no es válida; se renuevan los usuarios de la app."
        $passwords = @{
            auth = Nueva-Clave 'Db!'; cat = Nueva-Clave 'Db!'; prod = Nueva-Clave 'Db!'
            ped = Nueva-Clave 'Db!'; cont = Nueva-Clave 'Db!'
        }
        Ejecutar-Sql $clienteLocal (Sql-De-Esquemas $passwords) 'root' ''
        Escribir-Env $jdbc $passwords 'local'
        Write-Host "MySQL local corregido en puerto $Puerto con usuarios dedicados."
        return
    }
    if (-not (Test-Path -LiteralPath (Join-Path $datos 'mysql'))) { throw "Faltan datos inicializados en $datos" }
} else {
    if (Test-Path -LiteralPath $datos) {
        $contenido = @(Get-ChildItem -LiteralPath $datos -Force -ErrorAction SilentlyContinue)
        if ($contenido.Count -gt 0) { throw "El directorio $datos no está vacío. Bórralo o pasa -DirectorioDatos uno nuevo." }
    }
    New-Item -ItemType Directory -Path $datos -Force | Out-Null
    $mysqldInicial = Join-Path $MysqlHome 'bin\mysqld.exe'
    & $mysqldInicial --no-defaults "--basedir=$MysqlHome" "--datadir=$datos" --initialize-insecure 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "No se pudo inicializar MySQL: $LASTEXITCODE" }
}

$mysqlHome = $MysqlHome
$mysqld = Join-Path $mysqlHome 'bin\mysqld.exe'
$cliente = Join-Path $mysqlHome 'bin\mysql.exe'
if (-not (Test-Path -LiteralPath $mysqld) -or -not (Test-Path -LiteralPath $cliente)) {
    throw "No encontré mysqld.exe/mysql.exe en $mysqlHome"
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

function Iniciar-Mysql {
    $argumentos = @('--no-defaults', ('"--basedir=' + $mysqlHome + '"'), ('"--datadir=' + $datos + '"'), "--port=$Puerto", '--bind-address=127.0.0.1', '--mysqlx=OFF')
    $proceso = Start-Process -FilePath $mysqld -ArgumentList $argumentos -WorkingDirectory $local -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs 'mysql.out') -RedirectStandardError (Join-Path $logs 'mysql.err')
    Esperar-Mysql $proceso
    $conexion = Get-NetTCPConnection -State Listen -LocalPort $Puerto -ErrorAction Stop
    [IO.File]::WriteAllText($pidFile, "$($conexion.OwningProcess)`r`n", [Text.UTF8Encoding]::new($false))
}

Iniciar-Mysql
if (-not (Test-Path -LiteralPath $envFile)) {
    $passwords = @{
        auth = Nueva-Clave 'Db!'; cat = Nueva-Clave 'Db!'; prod = Nueva-Clave 'Db!'
        ped = Nueva-Clave 'Db!'; cont = Nueva-Clave 'Db!'
    }
    Ejecutar-Sql $cliente (Sql-De-Esquemas $passwords) 'root' ''
    Escribir-Env $jdbc $passwords 'local'
    Write-Host "MySQL local listo en puerto $Puerto con cinco esquemas y usuarios dedicados."
    Write-Host "Claves web en $local\credenciales.txt"
}
