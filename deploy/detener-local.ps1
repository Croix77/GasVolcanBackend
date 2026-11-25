$ErrorActionPreference = 'Stop'
$archivo = Join-Path (Join-Path $PSScriptRoot '.local') 'pids.txt'
if (-not (Test-Path -LiteralPath $archivo)) { throw "No existe $archivo" }
$envFile = Join-Path (Join-Path $PSScriptRoot '.local') 'env.ps1'
if (Test-Path -LiteralPath $envFile) { . $envFile }
foreach ($linea in Get-Content -LiteralPath $archivo) {
    if (-not $linea -or $linea -notmatch '^(.+)=(\d+)$') { continue }
    $nombre = $Matches[1]
    if ($nombre -eq 'mysql-local' -and $env:GV_MYSQL_MODO -eq 'externo') {
        Write-Host "OMITIDO · mysql-local es externo"
        continue
    }
    $id = [int]$Matches[2]
    $proceso = Get-Process -Id $id -ErrorAction SilentlyContinue
    if ($proceso) {
        Stop-Process -Id $id -Force
        Write-Host "DETENIDO · $nombre pid=$id"
    } else {
        Write-Host "YA DETENIDO · $nombre pid=$id"
    }
}
Write-Host 'TODO_DETENIDO'
