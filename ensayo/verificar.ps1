param(
    [Parameter(Mandatory)] [string]$AuthJar,
    [Parameter(Mandatory)] [string]$MysqlHome,
    [Parameter(Mandatory)] [string]$Java
)
$ErrorActionPreference = 'Stop'
$authJar = (Resolve-Path -LiteralPath $AuthJar).Path
$java = (Resolve-Path -LiteralPath $Java).Path
$mysqlHome = (Resolve-Path -LiteralPath $MysqlHome).Path
$mysqlServer = Join-Path $mysqlHome 'bin/mysqld.exe'
$jars = @{}
foreach ($nombre in 'ms-categorias','ms-productos','ms-pedidos','ms-despacho') {
    $jars[$nombre] = (Resolve-Path (Join-Path $PSScriptRoot "$nombre/target/$nombre-0.0.1-SNAPSHOT.jar")).Path
}
$temp = Join-Path ([IO.Path]::GetTempPath()) ('gas-volcan-ensayo-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp | Out-Null
$datosMysql = Join-Path $temp 'mysql'
$initSql = Join-Path $temp 'mysql-init.sql'
$procesos = [Collections.Generic.List[Diagnostics.Process]]::new()
$http = [Net.Http.HttpClient]::new()
$http.Timeout = [TimeSpan]::FromSeconds(20)
$exito = $false

function Puerto-Libre {
    $socket = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $socket.Start()
    try { return $socket.LocalEndpoint.Port } finally { $socket.Stop() }
}
function Esperar-Puerto([int]$puerto, [Diagnostics.Process]$proceso, [string]$nombre) {
    $fin = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $fin) {
        if ($proceso.HasExited) { throw "$nombre terminó con código $($proceso.ExitCode)." }
        $conexion = [Net.Sockets.TcpClient]::new()
        try { $conexion.Connect([Net.IPAddress]::Loopback, $puerto); return }
        catch [Net.Sockets.SocketException] { Start-Sleep -Milliseconds 300 }
        finally { $conexion.Dispose() }
    }
    throw "$nombre no abrió el puerto $puerto."
}
function Nueva-Clave { 'T!' + [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(18)) + 'Ab8' }
function Probar([bool]$condicion, [string]$caso) {
    if (!$condicion) { throw "Falló: $caso" }
    Write-Host "OK · $caso"
}
function Abrir-Proceso([string]$ejecutable, [string[]]$argumentos, [string]$nombre, [hashtable]$variables = @{}) {
    $opciones = @{
        FilePath=$ejecutable; ArgumentList=$argumentos; WorkingDirectory=$temp; Environment=$variables; PassThru=$true; WindowStyle='Hidden'
        RedirectStandardOutput=Join-Path $temp "$nombre.log"; RedirectStandardError=Join-Path $temp "$nombre.err"
    }
    $proceso = Start-Process @opciones
    $procesos.Add($proceso)
    return $proceso
}
function Abrir-Jar([string]$jar, [string]$nombre, [hashtable]$variables) {
    Abrir-Proceso $java @('-Xms32m','-Xmx320m','-jar',('"' + $jar + '"'),'--server.address=127.0.0.1') $nombre $variables
}
function Nueva-Peticion([string]$metodo, [string]$url, [string]$token, [object]$body) {
    $r = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($metodo), $url)
    if ($token) { $r.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token) }
    if ($null -ne $body) {
        $json = ConvertTo-Json -Compress -Depth 10 -InputObject $body
        $r.Content = [Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, 'application/json')
    }
    return $r
}
function Resultado([Net.Http.HttpResponseMessage]$respuesta) {
    try {
        $texto = $respuesta.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $data = if ($texto) { ,(ConvertFrom-Json -NoEnumerate -InputObject $texto) } else { $null }
        return @{ Estado = [int]$respuesta.StatusCode; Datos = $data }
    } finally { $respuesta.Dispose() }
}
function Llamar([string]$metodo, [string]$url, [int]$estado, [string]$token = '', [object]$body = $null) {
    $r = Nueva-Peticion $metodo $url $token $body
    try {
        $salida = Resultado ($http.SendAsync($r).GetAwaiter().GetResult())
        if ($salida.Estado -ne $estado) { throw "$metodo $url esperaba HTTP $estado. Respondió $($salida.Estado)." }
        return ,$salida.Datos
    } finally { $r.Dispose() }
}
function Concurrencia([string]$url, [string]$tokenA, [object]$bodyA, [string]$tokenB, [object]$bodyB) {
    $a = Nueva-Peticion 'POST' $url $tokenA $bodyA
    $b = Nueva-Peticion 'POST' $url $tokenB $bodyB
    try {
        $tareaA = $http.SendAsync($a)
        $tareaB = $http.SendAsync($b)
        return @(Resultado ($tareaA.GetAwaiter().GetResult()); Resultado ($tareaB.GetAwaiter().GetResult()))
    } finally { $a.Dispose(); $b.Dispose() }
}
function Intento([object[]]$items) {
    return @{
        nombre='Cliente del ensayo'; telefono='912345678'; direccion='Calle ficticia 123'; comuna='Chillán'
        latitudEntrega=-36.61; longitudEntrega=-72.1; referenciaCompra=[guid]::NewGuid().ToString(); items=$items
    }
}

try {
    $dbPort = Puerto-Libre
    $authPort = Puerto-Libre
    $categoriasPort = Puerto-Libre
    $productosPort = Puerto-Libre
    $pedidosPort = Puerto-Libre
    $despachoPort = Puerto-Libre
    $authBase = "http://127.0.0.1:$authPort"
    $categoriasBase = "http://127.0.0.1:$categoriasPort"
    $productoBase = "http://127.0.0.1:$productosPort"
    $pedidoBase = "http://127.0.0.1:$pedidosPort"
    $despacho = "http://127.0.0.1:$despachoPort/api/v1/despacho"
    $jwt = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $rootDbClave = Nueva-Clave
    $authDbClave = Nueva-Clave
    $categoriasDbClave = Nueva-Clave
    $productosDbClave = Nueva-Clave
    $pedidosDbClave = Nueva-Clave
    $adminClave = Nueva-Clave
    $clienteClave = Nueva-Clave
    $adminCorreo = 'admin@example.test'

    $inicializacion = Abrir-Proceso $mysqlServer @('--no-defaults','--initialize-insecure',('--basedir="' + $mysqlHome + '"'),('--datadir="' + $datosMysql + '"')) 'mysql-init'
    if (!$inicializacion.WaitForExit(45000) -or $inicializacion.ExitCode -ne 0) { throw 'No se pudo inicializar MySQL temporal.' }
    @"
CREATE DATABASE prueba_auth CHARACTER SET utf8mb4;
CREATE DATABASE prueba_categorias CHARACTER SET utf8mb4;
CREATE DATABASE prueba_productos CHARACTER SET utf8mb4;
CREATE DATABASE prueba_pedidos CHARACTER SET utf8mb4;
CREATE USER 'auth_ensayo'@'127.0.0.1' IDENTIFIED BY '$authDbClave';
CREATE USER 'categorias_ensayo'@'127.0.0.1' IDENTIFIED BY '$categoriasDbClave';
CREATE USER 'productos_ensayo'@'127.0.0.1' IDENTIFIED BY '$productosDbClave';
CREATE USER 'pedidos_ensayo'@'127.0.0.1' IDENTIFIED BY '$pedidosDbClave';
GRANT ALL PRIVILEGES ON prueba_auth.* TO 'auth_ensayo'@'127.0.0.1';
GRANT ALL PRIVILEGES ON prueba_categorias.* TO 'categorias_ensayo'@'127.0.0.1';
GRANT ALL PRIVILEGES ON prueba_productos.* TO 'productos_ensayo'@'127.0.0.1';
GRANT ALL PRIVILEGES ON prueba_pedidos.* TO 'pedidos_ensayo'@'127.0.0.1';
ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootDbClave';
"@ | Set-Content -LiteralPath $initSql -Encoding utf8NoBOM
    $mysql = Abrir-Proceso $mysqlServer @('--no-defaults',('--basedir="' + $mysqlHome + '"'),('--datadir="' + $datosMysql + '"'),"--port=$dbPort",'--bind-address=127.0.0.1','--mysqlx=OFF',('--init-file="' + $initSql + '"')) 'mysql-run'
    Esperar-Puerto $dbPort $mysql 'MySQL temporal'

    $jdbc = "jdbc:mysql://127.0.0.1:$dbPort"
    $opcionesJdbc = 'allowPublicKeyRetrieval=true&sslMode=DISABLED'
    $auth = Abrir-Jar $authJar 'auth' @{
        AUTH_PORT="$authPort"; JWT_SECRET=$jwt; SPRING_PROFILES_ACTIVE='local'
        AUTH_DB_URL="$jdbc/prueba_auth?$opcionesJdbc"; AUTH_DB_USER='auth_ensayo'; AUTH_DB_PASSWORD=$authDbClave
        AUTH_BOOTSTRAP_ADMIN='true'; AUTH_ADMIN_NAME='Persona admin'; AUTH_ADMIN_EMAIL=$adminCorreo; AUTH_ADMIN_PASSWORD=$adminClave
    }
    $categorias = Abrir-Jar $jars['ms-categorias'] 'categorias' @{
        PORT="$categoriasPort"; JWT_SECRET=$jwt
        DB_URL="$jdbc/prueba_categorias?$opcionesJdbc"; DB_USER='categorias_ensayo'; DB_PASSWORD=$categoriasDbClave
    }
    $productoVars = @{
        PORT="$productosPort"; JWT_SECRET=$jwt; CATEGORIAS_URL=$categoriasBase
        DB_URL="$jdbc/prueba_productos?$opcionesJdbc"; DB_USER='productos_ensayo'; DB_PASSWORD=$productosDbClave
    }
    $productos = Abrir-Jar $jars['ms-productos'] 'productos' $productoVars
    $pedidos = Abrir-Jar $jars['ms-pedidos'] 'pedidos' @{
        PORT="$pedidosPort"; JWT_SECRET=$jwt; PRODUCTOS_URL=$productoBase; APP_SEED_STOCK='true'
        DB_URL="$jdbc/prueba_pedidos?$opcionesJdbc"; DB_USER='pedidos_ensayo'; DB_PASSWORD=$pedidosDbClave
    }
    $reparto = Abrir-Jar $jars['ms-despacho'] 'despacho' @{
        PORT="$despachoPort"; JWT_SECRET=$jwt; PEDIDOS_URL=$pedidoBase; AUTH_URL=$authBase
    }

    Esperar-Puerto $authPort $auth 'ms-auth'
    Esperar-Puerto $categoriasPort $categorias 'ms-categorias'
    Esperar-Puerto $productosPort $productos 'ms-productos'
    Esperar-Puerto $pedidosPort $pedidos 'ms-pedidos'
    Esperar-Puerto $despachoPort $reparto 'ms-despacho'
    Start-Sleep -Seconds 2
    Remove-Item -LiteralPath $initSql -ErrorAction SilentlyContinue

    $login = "$authBase/api/auth/login"
    $admin = (Llamar 'POST' $login 200 '' @{correo=$adminCorreo;password=$adminClave}).token
    $tokens = @{}
    $cuentas = @{clienteA='CLIENTE';clienteB='CLIENTE';operadora='OPERADORA';repartidorA='REPARTIDOR';repartidorB='REPARTIDOR'}
    foreach ($alias in $cuentas.Keys) {
        $correo = "$($alias.ToLowerInvariant())@example.test"
        $null = Llamar 'POST' "$authBase/api/auth/usuarios" 201 $admin @{
            correo=$correo;nombre="Persona $alias";password=$clienteClave;rol=$cuentas[$alias]
        }
        $tokens[$alias] = (Llamar 'POST' $login 200 '' @{correo=$correo;password=$clienteClave}).token
    }
    $rutaProducto = "$productoBase/api/v1/productos/gas-5kg"
    $rutaPedido = "$pedidoBase/api/v1/pedidos"
    $stock5 = "$pedidoBase/api/v1/stock/gas-5kg"
    $stock15 = "$pedidoBase/api/v1/stock/gas-15kg"
    $null = Llamar 'GET' $rutaProducto 401
    $null = Llamar 'GET' $rutaPedido 401
    $null = Llamar 'GET' $despacho 401
    $null = Llamar 'GET' $despacho 403 $tokens.clienteA
    $categoria = Llamar 'GET' "$categoriasBase/api/v1/categorias/1" 200 $tokens.clienteA
    $ficha = Llamar 'GET' $rutaProducto 200 $tokens.clienteA
    Probar ($categoria.id -eq 1 -and $ficha.precio -eq 5900) 'Cinco servicios arrancan y respetan permisos JWT'

    $pedidoA = Intento @(@{id='gas-5kg';cantidad=1;precioEsperado=5900})
    $null = Llamar 'PUT' "$rutaProducto/precio" 200 $admin @{precio=6100}
    $null = Llamar 'POST' $rutaPedido 409 $tokens.clienteA $pedidoA
    Probar ((Llamar 'GET' $stock5 200 $tokens.clienteA).stock -eq 1) 'Un precio cambiado requiere confirmación y conserva la última unidad'
    $pedidoA.items[0].precioEsperado = 6100
    $pedidoB = Intento @(@{id='gas-5kg';cantidad=1;precioEsperado=6100})
    $carrera = Concurrencia $rutaPedido $tokens.clienteA $pedidoA $tokens.clienteB $pedidoB
    Probar ((($carrera.Estado | Sort-Object) -join ',') -eq '201,409') 'Dos clientes compran la última unidad: se confirma un solo pedido'
    Probar ((Llamar 'GET' $stock5 200 $tokens.clienteA).stock -eq 0) 'El stock termina en cero'
    $primero = $carrera[0].Estado -eq 201
    $ganador = if ($primero) { $carrera[0].Datos } else { $carrera[1].Datos }
    $cuerpoGanador = if ($primero) { $pedidoA } else { $pedidoB }
    $tokenGanador = if ($primero) { $tokens.clienteA } else { $tokens.clienteB }
    $tokenAjeno = if ($primero) { $tokens.clienteB } else { $tokens.clienteA }
    Probar ($ganador.total -eq 6100 -and $ganador.items[0].precioUnitario -eq 6100) 'El pedido confirma y guarda el precio vigente'
    $null = Llamar 'GET' "$rutaPedido/$($ganador.id)" 404 $tokenAjeno

    $mismo = Intento @(@{id='gas-15kg';cantidad=2;precioEsperado=16000})
    $repetidos = Concurrencia $rutaPedido $tokens.clienteA $mismo $tokens.clienteA $mismo
    Probar ((($repetidos.Estado | Sort-Object) -join ',') -eq '200,201' -and $repetidos[0].Datos.id -eq $repetidos[1].Datos.id) 'Dos envíos simultáneos con la misma referencia devuelven el mismo pedido'
    Probar ((Llamar 'GET' $stock15 200 $tokens.clienteA).stock -eq 2) 'Los reintentos descuentan inventario una sola vez'
    $compraParcial = Intento @(@{id='gas-15kg';cantidad=1},@{id='gas-5kg';cantidad=1})
    $null = Llamar 'POST' $rutaPedido 409 $tokens.clienteA $compraParcial
    Probar ((Llamar 'GET' $stock15 200 $tokens.clienteA).stock -eq 2) 'Si falta el segundo artículo, se revierte el descuento del primero'

    Stop-Process -Id $productos.Id
    $productos.WaitForExit(5000) | Out-Null
    $replay = Llamar 'POST' $rutaPedido 200 $tokenGanador $cuerpoGanador
    Probar ($replay.id -eq $ganador.id) 'El cliente recupera un pedido confirmado aunque el catálogo esté caído'
    $nuevo = Intento @(@{id='gas-15kg';cantidad=1})
    $null = Llamar 'POST' $rutaPedido 503 $tokens.clienteA $nuevo
    Probar ((Llamar 'GET' $stock15 200 $tokens.clienteA).stock -eq 2) 'Un pedido nuevo con catálogo caído devuelve 503 sin tocar stock'
    $productos = Abrir-Jar $jars['ms-productos'] 'productos-reinicio' $productoVars
    Esperar-Puerto $productosPort $productos 'ms-productos reiniciado'
    $null = Llamar 'PUT' "$rutaProducto/precio" 200 $admin @{precio=6300}
    $historico = Llamar 'GET' "$rutaPedido/$($ganador.id)" 200 $tokenGanador
    Probar ($historico.total -eq 6100) 'Un cambio posterior de precio conserva el monto del pedido anterior'

    $entrega = "$despacho/$($ganador.id)"
    $null = Llamar 'PATCH' "$rutaPedido/$($ganador.id)/asignacion" 403 $tokens.operadora @{correoRepartidor='no-existe@example.test'}
    $null = Llamar 'PATCH' "$entrega/asignacion" 409 $tokens.operadora @{correoRepartidor='no-existe@example.test'}
    $asignado = Llamar 'PATCH' "$entrega/asignacion" 200 $tokens.operadora @{correoRepartidor='repartidora@example.test'}
    Probar ($asignado.estado -eq 'ASIGNADO') 'La asignación pública pasa por Despacho y exige un repartidor activo'
    $null = Llamar 'GET' $entrega 404 $tokens.repartidorB
    $null = Llamar 'PATCH' "$entrega/estado" 403 $tokens.repartidorB @{estado='EN_CAMINO'}
    $null = Llamar 'PATCH' "$entrega/estado" 409 $tokens.repartidorA @{estado='ENTREGADO'}
    $null = Llamar 'PATCH' "$entrega/estado" 200 $tokens.repartidorA @{estado='EN_CAMINO'}
    $null = Llamar 'PUT' "$entrega/ubicacion" 200 $tokens.repartidorA @{latitud=-36.62;longitud=-72.11}
    $entregado = Llamar 'PATCH' "$entrega/estado" 200 $tokens.repartidorA @{estado='ENTREGADO'}
    $repeticion = Llamar 'PATCH' "$entrega/estado" 200 $tokens.repartidorA @{estado='ENTREGADO'}
    Probar ($entregado.pagoEfectivo -and $entregado.fechaEntrega -and $entregado.fechaEntrega -eq $repeticion.fechaEntrega) 'Se rechaza al repartidor ajeno y un reintento conserva el registro de pago'
    $vistaCliente = Llamar 'GET' "$rutaPedido/$($ganador.id)" 200 $tokenGanador
    Probar ($vistaCliente.estado -eq 'ENTREGADO' -and [Math]::Abs($vistaCliente.latitudRepartidor + 36.62) -lt 0.000001) 'El cliente ve la entrega y la ubicación guardada desde despacho'
    $exito = $true
} finally {
    $http.Dispose()
    for ($i = $procesos.Count - 1; $i -ge 0; $i--) {
        $proceso = $procesos[$i]
        if (!$proceso.HasExited) {
            Stop-Process -Id $proceso.Id -ErrorAction SilentlyContinue
            $proceso.WaitForExit(5000) | Out-Null
        }
    }
    if (Test-Path -LiteralPath $initSql) { Remove-Item -LiteralPath $initSql -ErrorAction SilentlyContinue }
    if ($exito) {
        $rutaReal = (Resolve-Path -LiteralPath $temp).Path
        $raizTemporal = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\','/')
        if ([IO.Path]::GetDirectoryName($rutaReal) -eq $raizTemporal -and
            [IO.Path]::GetFileName($rutaReal) -match '^gas-volcan-ensayo-[a-f0-9]{32}$') {
            # Windows puede conservar abierto un archivo de MySQL durante unos instantes al salir.
            for ($intento = 0; $intento -lt 8; $intento++) {
                try {
                    Remove-Item -LiteralPath $rutaReal -Recurse -Force -ErrorAction Stop
                    break
                } catch {
                    if (!(Test-Path -LiteralPath $rutaReal)) { break }
                    Start-Sleep -Milliseconds 500
                }
            }
            if (Test-Path -LiteralPath $rutaReal) {
                Write-Warning "La prueba pasó. Windows conserva archivos temporales en $rutaReal"
            }
        }
    } else {
        Write-Host "Logs del ensayo: $temp"
    }
}
