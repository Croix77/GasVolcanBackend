param([int]$MysqlPuerto = 3307)
$ErrorActionPreference = 'Stop'
$despliegue = $PSScriptRoot
$local = Join-Path $despliegue '.local'
$envFile = Join-Path $local 'env.ps1'
if (Test-Path -LiteralPath $envFile) { . $envFile }
$puertos = [ordered]@{
    'mysql-local' = $MysqlPuerto; 'ms-auth' = 8081; 'ms-categorias' = 8082; 'ms-productos' = 8083
    'ms-pedidos' = 8084; 'ms-despacho' = 8085; 'ms-contacto' = 8086; 'vite' = 5173
}
foreach ($nombre in $puertos.Keys) {
    $conexion = Get-NetTCPConnection -State Listen -LocalPort $puertos[$nombre] -ErrorAction SilentlyContinue
    if ($conexion) { "OK · $nombre puerto=$($puertos[$nombre]) pid=$($conexion.OwningProcess)" }
    else { "FALTA · $nombre puerto=$($puertos[$nombre])" }
}
$credenciales = @(
    @{ correo = $env:AUTH_ADMIN_EMAIL; password = $env:AUTH_ADMIN_PASSWORD },
    @{ correo = 'cliente@gasvolcan.cl'; password = $env:DEMO_CLIENTE_PASSWORD },
    @{ correo = 'vendedor@gasvolcan.cl'; password = $env:DEMO_VENDEDOR_PASSWORD },
    @{ correo = 'operadora@gasvolcan.cl'; password = $env:DEMO_OPERADORA_PASSWORD },
    @{ correo = 'repartidor@gasvolcan.cl'; password = $env:DEMO_REPARTIDOR_PASSWORD }
)
foreach ($credencial in $credenciales) {
    try {
        $respuesta = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:5173/api/auth/login' -ContentType 'application/json' -Body ($credencial | ConvertTo-Json -Compress)
        "LOGIN OK · $($credencial.correo) rol=$($respuesta.rol)"
    } catch {
        "LOGIN FALLO · $($credencial.correo)"
    }
}
