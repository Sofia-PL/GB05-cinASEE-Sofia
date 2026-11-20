#Requires -Version 5.1
<#
.SYNOPSIS
    Muestra el estado de los contenedores y comprueba los endpoints de CinAsee.

.EXAMPLE
    .\scripts\estado.ps1
#>
[CmdletBinding()]
param()

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

function Get-CodigoHttp {
    param([string]$Url)

    try {
        $respuesta = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 -MaximumRedirection 3
        return [int]$respuesta.StatusCode
    } catch {
        $respuesta = $_.Exception.Response
        if ($null -ne $respuesta -and $null -ne $respuesta.StatusCode) {
            return [int]$respuesta.StatusCode
        }
        return 0
    }
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

    Write-Host ""
    Write-Host "== Contenedores ==" -ForegroundColor Cyan
    $argumentos = @($compose.Argumentos) + @('ps')
    & $compose.Ejecutable @argumentos
    if ($LASTEXITCODE -ne 0) {
        throw "Fallo 'docker compose ps' (codigo $LASTEXITCODE)."
    }

    $checks = @(
        @{ Nombre = 'C3 IAM';       Url = 'http://localhost:8083/v1/iam/.well-known/jwks.json'; Acepta = @(200) },
        @{ Nombre = 'C1 Cartelera'; Url = 'http://localhost:8081/api/v1/movies'; Acepta = @(200) },
        @{ Nombre = 'C2 Reservas';  Url = 'http://localhost:8082/v1/reservations/stats'; Acepta = @(401, 403) },
        @{ Nombre = 'C4 Analitica'; Url = 'http://localhost:8084/api/metrics/occupancy?cinemaId=550e8400-e29b-41d4-a716-446655440001'; Acepta = @(401, 403) },
        @{ Nombre = 'Gateway';      Url = 'http://localhost:8080/actuator/health'; Acepta = @(200) },
        @{ Nombre = 'Frontend';     Url = 'http://localhost:3000/'; Acepta = @(200) },
        @{ Nombre = 'Media';        Url = 'http://localhost:8085/media/posters/ponyo.jpg'; Acepta = @(200) }
    )

    Write-Host ""
    Write-Host "== Endpoints ==" -ForegroundColor Cyan

    $fallidos = 0
    foreach ($check in $checks) {
        $codigo = Get-CodigoHttp -Url $check.Url
        if ($check.Acepta -contains $codigo) {
            Write-Host ("  {0,-14} {1,-70} OK (HTTP {2})" -f $check.Nombre, $check.Url, $codigo) -ForegroundColor Green
        } else {
            Write-Host ("  {0,-14} {1,-70} FALLO (HTTP {2})" -f $check.Nombre, $check.Url, $codigo) -ForegroundColor Red
            $fallidos++
        }
    }

    Write-Host ""
    if ($fallidos -gt 0) {
        Write-Host "  $fallidos servicio(s) no responden. Revisa 'docker compose logs'." -ForegroundColor Yellow
        exit 1
    }

    Write-Host "  Todos los servicios responden." -ForegroundColor Green
} catch {
    Write-Host "  [X] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
