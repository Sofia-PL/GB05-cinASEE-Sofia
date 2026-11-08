<#
.SYNOPSIS
    Genera un JWT de PRUEBA (HS256) para el perfil "isolated" del Componente 2.

.DESCRIPTION
    En producción los tokens los emite el Componente 3 (RS256). Para probar
    Reservas sin el Componente 3, el perfil "isolated" acepta tokens firmados con
    un secreto HMAC de desarrollo. Este script crea esos tokens con los mismos
    claims que pone el Componente 3: sub, role, iss, iat, exp, jti.

.EXAMPLE
    # Cliente registrado 5590 (tiene descuento de fidelización en WireMock)
    .\scripts\generate-dev-token.ps1 -Subject 5590 -Role CLIENTE_REGISTRADO

.EXAMPLE
    # Invitado
    .\scripts\generate-dev-token.ps1 -Subject "guest:demo-1" -Role VISITANTE

.EXAMPLE
    # Personal de taquilla (estadísticas, validación en puerta)
    .\scripts\generate-dev-token.ps1 -Subject 1 -Role STAFF_TAQUILLA
#>
param(
    [string] $Subject = "5590",
    [ValidateSet("VISITANTE", "CLIENTE_REGISTRADO", "STAFF_TAQUILLA", "ADMIN")]
    [string] $Role = "CLIENTE_REGISTRADO",
    [int] $Minutes = 120,
    # Debe coincidir con app.security.dev-hmac-secret (application-isolated.yaml)
    [string] $Secret = "cinasee-dev-secret-solo-para-pruebas-locales-0123456789",
    [string] $Issuer = "http://localhost:8083/v1/iam"
)

function ConvertTo-Base64Url([byte[]] $bytes) {
    [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$header = @{ alg = "HS256"; typ = "JWT" } | ConvertTo-Json -Compress
$claims = [ordered]@{
    iss  = $Issuer
    sub  = $Subject
    role = $Role
    iat  = $now
    exp  = $now + ($Minutes * 60)
    jti  = [guid]::NewGuid().ToString()
}
if ($Role -eq "VISITANTE") { $claims.guest = $true }
$payload = $claims | ConvertTo-Json -Compress

$utf8 = [System.Text.Encoding]::UTF8
$unsigned = (ConvertTo-Base64Url $utf8.GetBytes($header)) + "." + (ConvertTo-Base64Url $utf8.GetBytes($payload))

$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = $utf8.GetBytes($Secret)
$signature = ConvertTo-Base64Url $hmac.ComputeHash($utf8.GetBytes($unsigned))

Write-Output "$unsigned.$signature"
