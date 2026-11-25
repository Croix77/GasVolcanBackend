$ErrorActionPreference = 'Stop'
$archivo = Join-Path (Join-Path $PSScriptRoot '.local') 'pids.txt'
if (-not (Test-Path -LiteralPath $archivo)) { throw "No existe $archivo" }
foreach ($linea in Get-Content -LiteralPath $archivo) {
    if (-not $linea -or $linea -notmatch '^(.+)=(\d+)$') { continue }
    $nombre = $Matches[1]
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
