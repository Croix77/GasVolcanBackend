param(
    [Parameter(Mandatory)] [string]$AuthJar,
    [Parameter(Mandatory)] [string]$MysqlHome,
    [Parameter(Mandatory)] [string]$Java,
    [string]$LegacyJar,
    [string]$Frontend,
    [string]$Nginx,
    [string]$Edge
)
$ErrorActionPreference = 'Stop'
$authJar = (Resolve-Path -LiteralPath $AuthJar).Path
$java = (Resolve-Path -LiteralPath $Java).Path
$mysqlHome = (Resolve-Path -LiteralPath $MysqlHome).Path
$mysqlServer = Join-Path $mysqlHome 'bin/mysqld.exe'
$directorio = Split-Path -Parent $PSScriptRoot
$jars = @{}
foreach ($nombre in 'ms-categorias','ms-productos','ms-pedidos','ms-despacho') {
    $jars[$nombre] = (Resolve-Path (Join-Path $directorio "$nombre/target/$nombre-0.0.1-SNAPSHOT.jar")).Path
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
    $legacyPort = Puerto-Libre
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
    $catalogoToken = Nueva-Clave
    $legacyDbClave = Nueva-Clave
    $usarSemillas = if ($LegacyJar) { 'false' } else { 'true' }

    $inicializacion = Abrir-Proceso $mysqlServer @('--no-defaults','--initialize-insecure',('--basedir="' + $mysqlHome + '"'),('--datadir="' + $datosMysql + '"')) 'mysql-init'
    if (!$inicializacion.WaitForExit(45000) -or $inicializacion.ExitCode -ne 0) { throw 'No se pudo inicializar MySQL temporal.' }
    @"
CREATE DATABASE gas_volcan_auth CHARACTER SET utf8mb4;
CREATE DATABASE gas_volcan_categorias CHARACTER SET utf8mb4;
CREATE DATABASE gas_volcan_productos CHARACTER SET utf8mb4;
CREATE DATABASE gas_volcan_pedidos CHARACTER SET utf8mb4;
CREATE DATABASE gas_volcan_negocio CHARACTER SET utf8mb4;
CREATE USER 'auth_ensayo'@'127.0.0.1' IDENTIFIED BY '$authDbClave';
CREATE USER 'categorias_ensayo'@'127.0.0.1' IDENTIFIED BY '$categoriasDbClave';
CREATE USER 'productos_ensayo'@'127.0.0.1' IDENTIFIED BY '$productosDbClave';
CREATE USER 'pedidos_ensayo'@'127.0.0.1' IDENTIFIED BY '$pedidosDbClave';
GRANT ALL PRIVILEGES ON gas_volcan_auth.* TO 'auth_ensayo'@'127.0.0.1';
GRANT ALL PRIVILEGES ON gas_volcan_categorias.* TO 'categorias_ensayo'@'127.0.0.1';
GRANT ALL PRIVILEGES ON gas_volcan_productos.* TO 'productos_ensayo'@'127.0.0.1';
GRANT ALL PRIVILEGES ON gas_volcan_pedidos.* TO 'pedidos_ensayo'@'127.0.0.1';
CREATE USER 'legacy_ensayo'@'127.0.0.1' IDENTIFIED BY '$legacyDbClave';
GRANT ALL PRIVILEGES ON gas_volcan_negocio.* TO 'legacy_ensayo'@'127.0.0.1';
ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootDbClave';
"@ | Set-Content -LiteralPath $initSql -Encoding utf8NoBOM
    $mysql = Abrir-Proceso $mysqlServer @('--no-defaults',('--basedir="' + $mysqlHome + '"'),('--datadir="' + $datosMysql + '"'),"--port=$dbPort",'--bind-address=127.0.0.1','--mysqlx=OFF',('--init-file="' + $initSql + '"')) 'mysql-run'
    Esperar-Puerto $dbPort $mysql 'MySQL temporal'

    $jdbc = "jdbc:mysql://127.0.0.1:$dbPort"
    $opcionesJdbc = 'allowPublicKeyRetrieval=true&sslMode=DISABLED'
    $auth = Abrir-Jar $authJar 'auth' @{
        AUTH_PORT="$authPort"; JWT_SECRET=$jwt; SPRING_PROFILES_ACTIVE='local'
        AUTH_DB_URL="$jdbc/gas_volcan_auth?$opcionesJdbc"; AUTH_DB_USER='auth_ensayo'; AUTH_DB_PASSWORD=$authDbClave
        AUTH_BOOTSTRAP_ADMIN='true'; AUTH_ADMIN_NAME='Persona admin'; AUTH_ADMIN_EMAIL=$adminCorreo; AUTH_ADMIN_PASSWORD=$adminClave
    }
    $categorias = Abrir-Jar $jars['ms-categorias'] 'categorias' @{
        PORT="$categoriasPort"; JWT_SECRET=$jwt; CATALOGO_INTERNO_TOKEN=$catalogoToken; APP_SEED_CATALOGO=$usarSemillas
        DB_URL="$jdbc/gas_volcan_categorias?$opcionesJdbc"; DB_USER='categorias_ensayo'; DB_PASSWORD=$categoriasDbClave
    }
    $productoVars = @{
        PORT="$productosPort"; JWT_SECRET=$jwt; CATEGORIAS_URL=$categoriasBase; CATALOGO_INTERNO_TOKEN=$catalogoToken; APP_SEED_CATALOGO=$usarSemillas
        DB_URL="$jdbc/gas_volcan_productos?$opcionesJdbc"; DB_USER='productos_ensayo'; DB_PASSWORD=$productosDbClave
    }
    Esperar-Puerto $categoriasPort $categorias 'ms-categorias'
    $productos = Abrir-Jar $jars['ms-productos'] 'productos' $productoVars
    $pedidos = Abrir-Jar $jars['ms-pedidos'] 'pedidos' @{
        PORT="$pedidosPort"; JWT_SECRET=$jwt; PRODUCTOS_URL=$productoBase; APP_SEED_STOCK=$usarSemillas
        DB_URL="$jdbc/gas_volcan_pedidos?$opcionesJdbc"; DB_USER='pedidos_ensayo'; DB_PASSWORD=$pedidosDbClave
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
    $cuentasIds = @{}
    $cuentas = @{clienteA='CLIENTE';clienteB='CLIENTE';operadora='OPERADORA';repartidorA='REPARTIDOR';repartidorB='REPARTIDOR'}
    foreach ($alias in $cuentas.Keys) {
        $correo = "$($alias.ToLowerInvariant())@example.test"
        $creada = Llamar 'POST' "$authBase/api/auth/usuarios" 201 $admin @{
            correo=$correo;nombre="Persona $alias";password=$clienteClave;rol=$cuentas[$alias]
        }
        $cuentasIds[$alias] = $creada.id
        $tokens[$alias] = (Llamar 'POST' $login 200 '' @{correo=$correo;password=$clienteClave}).token
    }
    if ($LegacyJar) {
        $legacy = Abrir-Jar (Resolve-Path -LiteralPath $LegacyJar).Path 'negocio-anterior' @{
            PORT="$legacyPort"; JWT_SECRET=$jwt; APP_SEED_CATALOGO='true'
            DB_URL="$jdbc/gas_volcan_negocio?$opcionesJdbc"; DB_USER='legacy_ensayo'; DB_PASSWORD=$legacyDbClave
        }
        Esperar-Puerto $legacyPort $legacy 'ms-negocio anterior'
        $legacyBase = "http://127.0.0.1:$legacyPort/api/v1"
        Start-Sleep -Seconds 2
        $catLegada = Llamar 'POST' "$legacyBase/categorias" 201 $admin @{nombre='Categoría que ya existía'}
        $compraLegada = Intento @(@{id='gas-11kg';cantidad=2})
        $historicoLegado = Llamar 'POST' "$legacyBase/pedidos" 201 $tokens.clienteA $compraLegada
        $stockLegado = Llamar 'GET' "$legacyBase/productos/gas-11kg" 200 $tokens.clienteA
        $null = Llamar 'PATCH' "$legacyBase/pedidos/$($historicoLegado.id)/asignacion" 200 $tokens.operadora @{correoRepartidor='repartidora@example.test'}
        $null = Llamar 'PATCH' "$legacyBase/pedidos/$($historicoLegado.id)/estado" 200 $tokens.repartidorA @{estado='EN_CAMINO'}
        $entregaLegada = Llamar 'PATCH' "$legacyBase/pedidos/$($historicoLegado.id)/estado" 200 $tokens.repartidorA @{estado='ENTREGADO'}
        $entregaLegada = Llamar 'GET' "$legacyBase/pedidos/$($historicoLegado.id)" 200 $tokens.clienteA
        Stop-Process -Id $legacy.Id
        $legacy.WaitForExit(5000) | Out-Null

        foreach ($intento in 1,2) {
            $entradaSql = @{
                FilePath=Join-Path $mysqlHome 'bin/mysql.exe'
                ArgumentList=@('--no-defaults','--protocol=TCP','-h127.0.0.1',"--port=$dbPort",'--user=root','--batch','--default-character-set=utf8mb4')
                Environment=@{MYSQL_PWD=$rootDbClave}
                RedirectStandardInput=Join-Path $PSScriptRoot 'migrar-negocio.sql'
                RedirectStandardOutput=Join-Path $temp "migracion-$intento.out"
                RedirectStandardError=Join-Path $temp "migracion-$intento.err"
                WindowStyle='Hidden';PassThru=$true
            }
            $importador=Start-Process @entradaSql
            $procesos.Add($importador)
            if (!$importador.WaitForExit(20000)) { throw 'La migración superó el tiempo de prueba.' }
            if ($intento -eq 1 -and $importador.ExitCode -ne 0) { Get-Content -LiteralPath $entradaSql.RedirectStandardError; throw 'La importación falló.' }
            if ($intento -eq 2) {
                $detalle = Get-Content -LiteralPath $entradaSql.RedirectStandardError -Raw
                Probar ($importador.ExitCode -ne 0 -and $detalle -match 'destino deben estar vac') 'La migración rechaza sobrescribir destinos poblados'
            }
        }
        $catNueva = Llamar 'GET' "$categoriasBase/api/v1/categorias/$($catLegada.id)" 200 $tokens.clienteA
        $stockNuevo = Llamar 'GET' "$pedidoBase/api/v1/stock/gas-11kg" 200 $tokens.clienteA
        $ordenNueva = Llamar 'GET' "$pedidoBase/api/v1/pedidos/$($historicoLegado.id)" 200 $tokens.clienteA
        $fichas = Llamar 'GET' "$productoBase/api/v1/productos" 200 $tokens.clienteA
        Probar ($catNueva.nombre -eq $catLegada.nombre -and $fichas.Count -eq 14 -and $stockNuevo.stock -eq $stockLegado.stock) 'La migración conserva el identificador de categoría, el catálogo y el inventario'
        Probar ($ordenNueva.id -eq $historicoLegado.id -and $ordenNueva.total -eq $historicoLegado.total -and
                $ordenNueva.items.Count -eq 1 -and $ordenNueva.items[0].cantidad -eq 2 -and
                $ordenNueva.pagoEfectivo -and $ordenNueva.fechaEntrega -eq $entregaLegada.fechaEntrega) 'La migración conserva los detalles, el total y el registro de efectivo del pedido anterior'
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

    $categoriasRuta = "$categoriasBase/api/v1/categorias"
    $productosRuta = "$productoBase/api/v1/productos"
    $cat = Llamar 'POST' $categoriasRuta 201 $admin @{nombre='Familia ensayo'}
    $fichaCreada = Llamar 'POST' $productosRuta 201 $admin @{
        codigo='nuevo-ensayo';nombre='Accesorio ensayo';descripcion='Ficha de verificación'
        unidad='Unidad';categoriaId=$cat.id;precio=1200;stockCritico=2;oferta=$false
    }
    $null = Llamar 'DELETE' "$categoriasRuta/$($cat.id)" 409 $admin
    $null = Llamar 'PUT' "$categoriasRuta/$($cat.id)" 200 $admin @{nombre='Familia renombrada'}
    $fichaLeida = Llamar 'GET' "$productosRuta/$($fichaCreada.id)" 200 $tokens.clienteA
    Probar ($fichaLeida.categoria -eq 'Familia renombrada') 'El catálogo actualiza nombres de categorías y protege las que tienen productos'

    $stockNuevoRuta = "$pedidoBase/api/v1/stock/$($fichaCreada.id)"
    $primeraCarga = Llamar 'PUT' $stockNuevoRuta 200 $admin @{stock=6;version=$null}
    Probar ($primeraCarga.stock -eq 6) 'El administrador registra stock por separado al crear un producto'
    $null = Llamar 'PUT' $stockNuevoRuta 403 $tokens.clienteA @{stock=8;version=$primeraCarga.version}
    $null = Llamar 'DELETE' "$productosRuta/$($fichaCreada.id)" 204 $admin
    $null = Llamar 'GET' "$productosRuta/$($fichaCreada.id)" 404 $tokens.clienteA

    $stock5Inicial = Llamar 'GET' $stock5 200 $admin
    $stock15Inicial = Llamar 'GET' $stock15 200 $admin
    $stock5Antes = Llamar 'PUT' $stock5 200 $admin @{stock=1;version=$stock5Inicial.version}
    $null = Llamar 'PUT' $stock15 200 $admin @{stock=4;version=$stock15Inicial.version}
    $pedidoA = Intento @(@{id='gas-5kg';cantidad=1;precioEsperado=5900})
    $null = Llamar 'PUT' "$rutaProducto/precio" 200 $admin @{precio=6100}
    $null = Llamar 'POST' $rutaPedido 409 $tokens.clienteA $pedidoA
    Probar ((Llamar 'GET' $stock5 200 $tokens.clienteA).stock -eq 1) 'Un precio cambiado requiere confirmación y conserva la última unidad'
    $pedidoA.items[0].precioEsperado = 6100
    $pedidoB = Intento @(@{id='gas-5kg';cantidad=1;precioEsperado=6100})
    $carrera = Concurrencia $rutaPedido $tokens.clienteA $pedidoA $tokens.clienteB $pedidoB
    Probar ((($carrera.Estado | Sort-Object) -join ',') -eq '201,409') 'Dos clientes compran la última unidad: se confirma un solo pedido'
    Probar ((Llamar 'GET' $stock5 200 $tokens.clienteA).stock -eq 0) 'El stock termina en cero'
    $null = Llamar 'PUT' $stock5 409 $admin @{stock=20;version=$stock5Antes.version}
    Probar ((Llamar 'GET' $stock5 200 $tokens.clienteA).stock -eq 0) 'Una edición con stock anterior no repone unidades que otra persona compró'
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
    $compraParcial = Intento @(@{id='gas-15kg';cantidad=1;precioEsperado=16000},@{id='gas-5kg';cantidad=1;precioEsperado=6100})
    $null = Llamar 'POST' $rutaPedido 409 $tokens.clienteA $compraParcial
    Probar ((Llamar 'GET' $stock15 200 $tokens.clienteA).stock -eq 2) 'Si falta el segundo artículo, se revierte el descuento del primero'

    Stop-Process -Id $productos.Id
    $productos.WaitForExit(5000) | Out-Null
    $replay = Llamar 'POST' $rutaPedido 200 $tokenGanador $cuerpoGanador
    Probar ($replay.id -eq $ganador.id) 'El cliente recupera un pedido confirmado aunque el catálogo esté caído'
    $nuevo = Intento @(@{id='gas-15kg';cantidad=1;precioEsperado=16000})
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
    $otroId = $repetidos[0].Datos.id
    $null = Llamar 'PATCH' "$despacho/$otroId/asignacion" 200 $tokens.operadora @{correoRepartidor='repartidorb@example.test'}
    $null = Llamar 'PATCH' "$authBase/api/auth/usuarios/$($cuentasIds['repartidorB'])/estado" 200 $admin @{activo=$false}
    $null = Llamar 'PATCH' "$despacho/$otroId/estado" 401 $tokens.repartidorB @{estado='EN_CAMINO'}
    Probar ((Llamar 'GET' "$rutaPedido/$otroId" 200 $tokens.clienteA).estado -eq 'ASIGNADO') 'Un repartidor desactivado no modifica su entrega aunque conserve el JWT'
    if ($Frontend) {
        if (!$Nginx -or !$Edge) { throw 'Pasa -Nginx y -Edge para comprobar React compilado en navegador.' }
        $frontPath = (Resolve-Path (Join-Path $Frontend 'dist')).Path.Replace('\','/')
        if (!(Test-Path -LiteralPath (Join-Path $frontPath 'index.html'))) { throw 'Compila React antes del ensayo.' }
        $nginxPath = (Resolve-Path -LiteralPath $Nginx).Path
        $edgePath = (Resolve-Path -LiteralPath $Edge).Path
        $nodePath = (Get-Command node.exe).Source
        $webPort = Puerto-Libre
        $cdp = Puerto-Libre
        $origen = "http://127.0.0.1:$webPort"
        New-Item -ItemType Directory -Path (Join-Path $temp 'logs') | Out-Null

        $config = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'gasvolcan.conf') -Raw)
        $config = $config.Replace('listen 80;', "listen 127.0.0.1:$webPort;").Replace('root /var/www/gasvolcan;', "root '$frontPath';")
        foreach ($par in @(@(8081,$authPort),@(8082,$categoriasPort),@(8083,$productosPort),@(8084,$pedidosPort),@(8085,$despachoPort))) {
            $config = $config.Replace("proxy_pass http://127.0.0.1:$($par[0]);", "proxy_pass http://127.0.0.1:$($par[1]);")
        }
        $prefijo = @"
daemon off;
master_process off;
error_log logs/error.log;
pid logs/nginx.pid;
events { worker_connections 256; }
http {
    default_type application/octet-stream;
    types { text/html html; application/javascript js mjs; text/css css; image/png png; image/jpeg jpg jpeg; image/webp webp; image/svg+xml svg; }
"@
        ($prefijo + [Environment]::NewLine + $config + '}') | Set-Content -LiteralPath (Join-Path $temp 'nginx.conf') -Encoding utf8NoBOM
        $nginxArgs = @('-p', ('"' + $temp.Replace('\','/') + '/"'), '-c', 'nginx.conf')
        $checkNginx = Abrir-Proceso $nginxPath ($nginxArgs + @('-t')) 'nginx-test'
        if (!$checkNginx.WaitForExit(10000) -or $checkNginx.ExitCode -ne 0) {
            Get-Content -LiteralPath (Join-Path $temp 'nginx-test.err')
            throw 'La configuración de Nginx no es válida.'
        }
        $web = Abrir-Proceso $nginxPath $nginxArgs 'nginx'
        Esperar-Puerto $webPort $web 'Nginx'
        $null = Llamar 'GET' "$origen/api/v1/categorias" 200 $tokens.clienteA
        $null = Llamar 'GET' "$origen/api/v1/productos" 200 $tokens.clienteA
        $null = Llamar 'GET' "$origen/api/v1/stock" 200 $tokens.clienteA
        $null = Llamar 'GET' "$origen/api/v1/despacho" 200 $tokens.operadora
        $privada = Invoke-WebRequest -Uri "$origen/api/v1/interno/pedidos/$($ganador.id)/estado" -SkipHttpErrorCheck -TimeoutSec 10
        Probar ($privada.StatusCode -eq 404) 'Nginx dirige las rutas públicas y oculta las rutas internas'

        $edgeDir = Join-Path $temp 'edge'
        $navegador = Abrir-Proceso $edgePath @('--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check',"--remote-debugging-port=$cdp",('--user-data-dir="' + $edgeDir + '"'),'about:blank') 'edge'
        Esperar-Puerto $cdp $navegador 'Edge de prueba'
        $browser = Abrir-Proceso $nodePath @(('"' + (Join-Path $PSScriptRoot 'verificar-navegador.mjs') + '"'),$origen,"$cdp") 'browser' @{SMOKE_CLIENT_PASSWORD=$clienteClave}
        if (!$browser.WaitForExit(78000) -or $browser.ExitCode -ne 0) {
            Get-Content -LiteralPath (Join-Path $temp 'browser.err')
            throw 'El recorrido de React en navegador falló.'
        }
        Get-Content -LiteralPath (Join-Path $temp 'browser.log')
    }
    $exito = $true
} finally {
    $http.Dispose()
    for ($i = $procesos.Count - 1; $i -ge 0; $i--) {
        $proceso = $procesos[$i]
        if (!$proceso.HasExited) {
            if ($proceso.ProcessName -eq 'msedge') { $proceso.Kill($true) }
            else { Stop-Process -Id $proceso.Id -ErrorAction SilentlyContinue }
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
