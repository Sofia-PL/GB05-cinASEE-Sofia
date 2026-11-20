#Requires -Version 5.1
<#
.SYNOPSIS
    Arranca la pila completa de CinAsee con Docker Compose.

.DESCRIPTION
    Comprueba Docker, construye y levanta todos los servicios, espera a que
    respondan por HTTP y muestra un resumen con URLs y credenciales.

.PARAMETER SinBuild
    Reutiliza las imagenes ya construidas (no pasa --build).

.PARAMETER Reiniciar
    Baja los contenedores antes de levantarlos. Conserva los datos.

.PARAMETER BorrarDatos
    Borra los volumenes (docker compose down -v) y regenera los seeds.
    Pide confirmacion.

.PARAMETER NoAbrir
    No abre el navegador al terminar.

.PARAMETER TimeoutSegundos
    Tiempo maximo de espera para que respondan los servicios. Por defecto 240.

.EXAMPLE
    .\scripts\iniciar.ps1

.EXAMPLE
    .\scripts\iniciar.ps1 -BorrarDatos
#>
[CmdletBinding()]
param(
    [switch]$SinBuild,
    [switch]$Reiniciar,
    [switch]$BorrarDatos,
    [switch]$NoAbrir,
    [ValidateRange(30, 3600)]
    [int]$TimeoutSegundos = 240
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

function Write-Titulo([string]$Texto) {
    Write-Host ""
    Write-Host "== $Texto ==" -ForegroundColor Cyan
}

function Write-Ok([string]$Texto) {
    Write-Host "  [OK] $Texto" -ForegroundColor Green
}

function Write-Aviso([string]$Texto) {
    Write-Host "  [!]  $Texto" -ForegroundColor Yellow
}

function Write-Fallo([string]$Texto) {
    Write-Host "  [X]  $Texto" -ForegroundColor Red
}

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

function Invoke-Compose {
    param([string[]]$Argumentos)

    $todos = @($script:Compose.Argumentos) + @($Argumentos)
    & $script:Compose.Ejecutable @todos
    if ($LASTEXITCODE -ne 0) {
        throw "Fallo 'docker compose $($Argumentos -join ' ')' (codigo $LASTEXITCODE)."
    }
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

function Get-PuertosOcupados {
    param([int[]]$Puertos)

    try {
        return @(Get-NetTCPConnection -State Listen -ErrorAction Stop |
            Where-Object { $Puertos -contains $_.LocalPort } |
            Select-Object -ExpandProperty LocalPort -Unique)
    } catch {
        return @()
    }
}

try {
    Write-Titulo "CinAsee - arranque con Docker"

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker no esta instalado o no esta en el PATH. Instala Docker Desktop."
    }

    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker no esta en marcha. Abre Docker Desktop, espera a que termine de iniciar y vuelve a ejecutar este script."
    }

    $script:Compose = Get-ComposeCommand
    Write-Ok "Docker y Docker Compose disponibles"

    $enMarcha = (Invoke-Compose @('ps', '--status', 'running', '--quiet')) -join ''
    if (-not $enMarcha) {
        $puertos = @(3000, 8080, 8081, 8082, 8083, 8084, 8085, 3306, 6379, 27017, 5433, 5434, 5672, 15672)
        $ocupados = Get-PuertosOcupados -Puertos $puertos
        if ($ocupados.Count -gt 0) {
            Write-Aviso "Puertos ya ocupados: $($ocupados -join ', '). Si pertenecen a otro programa, libéralos antes de continuar."
        }
    }

    if ($BorrarDatos) {
        Write-Aviso "Se van a borrar los volumenes de MySQL, MongoDB, PostgreSQL, Redis y RabbitMQ."
        $respuesta = Read-Host "Escribe 's' para continuar"
        if ($respuesta -notmatch '^[sSyY]$') {
            Write-Host "Cancelado." -ForegroundColor Yellow
            exit 0
        }
        Invoke-Compose @('down', '-v', '--remove-orphans')
        Write-Ok "Volumenes eliminados"
    } elseif ($Reiniciar) {
        Invoke-Compose @('down', '--remove-orphans')
        Write-Ok "Contenedores detenidos (datos conservados)"
    }

    $argumentosUp = @('up', '-d', '--remove-orphans')
    if (-not $SinBuild) {
        $argumentosUp += '--build'
    }

    Write-Titulo "Construyendo y levantando contenedores"
    Write-Host "  La primera vez puede tardar varios minutos." -ForegroundColor DarkGray
    Invoke-Compose $argumentosUp
    Write-Ok "Contenedores lanzados"

    $checks = @(
        @{ Nombre = 'C3 IAM';       Url = 'http://localhost:8083/v1/iam/.well-known/jwks.json'; Acepta = @(200); Servicio = 'componente3-usuarios' },
        @{ Nombre = 'C1 Cartelera'; Url = 'http://localhost:8081/api/v1/movies'; Acepta = @(200); Servicio = 'componente1-cartelera' },
        @{ Nombre = 'C2 Reservas';  Url = 'http://localhost:8082/v1/reservations/stats'; Acepta = @(401, 403); Servicio = 'componente2-reservas' },
        @{ Nombre = 'C4 Analitica'; Url = 'http://localhost:8084/api/metrics/occupancy?cinemaId=550e8400-e29b-41d4-a716-446655440001'; Acepta = @(401, 403); Servicio = 'componente4-analitica' },
        @{ Nombre = 'Gateway';      Url = 'http://localhost:8080/actuator/health'; Acepta = @(200); Servicio = 'componente5-gateway' },
        @{ Nombre = 'Frontend';     Url = 'http://localhost:3000/'; Acepta = @(200); Servicio = 'frontend' },
        @{ Nombre = 'Media';        Url = 'http://localhost:8085/media/posters/ponyo.jpg'; Acepta = @(200); Servicio = 'media-server' }
    )

    Write-Titulo "Esperando a que los servicios respondan"
    $inicio = Get-Date
    $limite = $inicio.AddSeconds($TimeoutSegundos)
    $resuelto = @{}
    foreach ($check in $checks) {
        $resuelto[$check.Nombre] = $false
    }

    while ((Get-Date) -lt $limite) {
        $pendientes = @($checks | Where-Object { -not $resuelto[$_.Nombre] })
        if ($pendientes.Count -eq 0) {
            break
        }

        foreach ($check in $pendientes) {
            $codigo = Get-CodigoHttp -Url $check.Url
            if ($check.Acepta -contains $codigo) {
                $resuelto[$check.Nombre] = $true
                Write-Ok "$($check.Nombre) responde (HTTP $codigo)"
            }
        }

        if (@($checks | Where-Object { -not $resuelto[$_.Nombre] }).Count -gt 0) {
            Start-Sleep -Seconds 3
        }
    }

    $fallidos = @($checks | Where-Object { -not $resuelto[$_.Nombre] })
    if ($fallidos.Count -gt 0) {
        Write-Fallo "No respondieron a tiempo: $(($fallidos | ForEach-Object { $_.Nombre }) -join ', ')"
        Write-Host ""
        Invoke-Compose @('ps')
        foreach ($fallido in $fallidos) {
            Write-Host ""
            Write-Aviso "Ultimos logs de $($fallido.Servicio):"
            Invoke-Compose @('logs', '--tail=100', $fallido.Servicio)
        }
        exit 1
    }

    $segundos = [math]::Round(((Get-Date) - $inicio).TotalSeconds)
    Write-Titulo "CinAsee listo en $segundos s"

    Write-Host "  Frontend      http://localhost:3000"
    Write-Host "  Gateway       http://localhost:8080"
    Write-Host "  C1 Cartelera  http://localhost:8081/api/v1"
    Write-Host "  C2 Reservas   http://localhost:8082/v1"
    Write-Host "  C3 IAM        http://localhost:8083/v1/iam"
    Write-Host "  C4 Analitica  http://localhost:8084/api"
    Write-Host "  RabbitMQ UI   http://localhost:15672  (cinasee / cinasee)"
    Write-Host "  Media         http://localhost:8085/media/posters"
    Write-Host ""
    Write-Host "  Credenciales demo:"
    Write-Host "    admin@cinasee.com    / Admin123!"
    Write-Host "    staff@cinasee.com    / Staff123!"
    Write-Host "    usuario@cinasee.com  / Usuario123!"
    Write-Host ""
    Write-Host "  Cine demo (Madrid): 550e8400-e29b-41d4-a716-446655440001"
    Write-Host "  Nota: la analitica empieza vacia hasta que se complete una compra." -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "  Parar:   .\DETENER.bat  (o .\scripts\detener.ps1)"
    Write-Host "  Estado:  .\scripts\estado.ps1"

    try {
        $peliculas = Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/movies?inShowcase=true' -TimeoutSec 5
        if (-not $peliculas -or @($peliculas).Count -eq 0) {
            Write-Host ""
            Write-Aviso "No hay peliculas en cartelera: las sesiones del seed caducan a los ~3 dias."
            Write-Aviso "Ejecuta '.\scripts\iniciar.ps1 -BorrarDatos' para regenerar el seed."
        }
    } catch {
        Write-Aviso "No se pudo comprobar la cartelera: $($_.Exception.Message)"
    }

    if (-not $NoAbrir) {
        Start-Process 'http://localhost:3000'
    }
} catch {
    Write-Fallo $_.Exception.Message
    exit 1
}
