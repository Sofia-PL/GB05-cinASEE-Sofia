#Requires -Version 5.1
<#
.SYNOPSIS
    Detiene la pila de CinAsee.

.PARAMETER BorrarDatos
    Borra tambien los volumenes (docker compose down -v). Pide confirmacion.

.EXAMPLE
    .\scripts\detener.ps1

.EXAMPLE
    .\scripts\detener.ps1 -BorrarDatos
#>
[CmdletBinding()]
param(
    [switch]$BorrarDatos
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

function Get-ComposeCommand {
    docker compose version *> $null
    if ($LASTEXITCODE -eq 0) {
        return @{ Ejecutable = 'docker'; Argumentos = @('compose') }
    }

    if (Get-Command docker-compose -ErrorAction SilentlyContinue) {
        docker-compose version *> $null
        if ($LASTEXITCODE -eq 0) {
            return @{ Ejecutable = 'docker-compose'; Argumentos = @() }
        }
    }

    throw "No se encontro 'docker compose' ni 'docker-compose'."
}

try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker no esta instalado o no esta en el PATH."
    }

    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker no esta en marcha. Abre Docker Desktop y vuelve a ejecutar este script."
    }

    $compose = Get-ComposeCommand

    if ($BorrarDatos) {
        Write-Host "Se van a borrar los volumenes de MySQL, MongoDB, PostgreSQL, Redis y RabbitMQ." -ForegroundColor Yellow
        $respuesta = Read-Host "Escribe 's' para continuar"
        if ($respuesta -notmatch '^[sSyY]$') {
            Write-Host "Cancelado." -ForegroundColor Yellow
            exit 0
        }
        $argumentos = @($compose.Argumentos) + @('down', '-v', '--remove-orphans')
        & $compose.Ejecutable @argumentos
        if ($LASTEXITCODE -ne 0) {
            throw "Fallo 'docker compose down -v' (codigo $LASTEXITCODE)."
        }
        Write-Host "CinAsee detenido y volumenes eliminados." -ForegroundColor Green
    } else {
        $argumentos = @($compose.Argumentos) + @('down', '--remove-orphans')
        & $compose.Ejecutable @argumentos
        if ($LASTEXITCODE -ne 0) {
            throw "Fallo 'docker compose down' (codigo $LASTEXITCODE)."
        }
        Write-Host "CinAsee detenido. Los datos se conservan." -ForegroundColor Green
    }
} catch {
    Write-Host "  [X] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
