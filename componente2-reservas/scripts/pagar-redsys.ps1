<#
.SYNOPSIS
    Abre la web de pago de pruebas de Redsys para una reserva, sin necesitar el frontend.

.DESCRIPTION
    Hace lo mismo que hará el frontend:
      1. Llama a POST /v1/reservations/{id}/payment/redsys (con el token del cliente).
      2. Genera un formulario HTML con los campos firmados que devuelve Reservas y lo
         abre en el navegador, que lo envía solo a la web de Redsys.
    Al terminar el pago, Redsys devuelve el navegador a la página de vuelta de Reservas
    (http://localhost:8082/v1/reservations/payments/redsys/return), que confirma la
    reserva y muestra las entradas.

    Requiere el microservicio arrancado con PAYMENT_MODE=redsys (el docker-compose.yml
    global ya lo activa) e internet.

.EXAMPLE
    .\scripts\pagar-redsys.ps1 -ReservationId 1 -Token $cliente
#>
param(
    [Parameter(Mandatory = $true)] [long] $ReservationId,
    [Parameter(Mandatory = $true)] [string] $Token,
    [string] $BaseUrl = "http://localhost:8082/v1"
)

$form = Invoke-RestMethod -Method Post "$BaseUrl/reservations/$ReservationId/payment/redsys" `
    -Headers @{ Authorization = "Bearer $Token" }

Write-Output "Pedido Redsys $($form.order): $($form.amount) $($form.currency). Butacas bloqueadas hasta $($form.paymentDeadline)."

# Formulario que se envía solo al cargarse (lo mismo que hará el frontend)
$inputs = ($form.fields.PSObject.Properties | ForEach-Object {
    "<input type='hidden' name='$($_.Name)' value='$([System.Net.WebUtility]::HtmlEncode($_.Value))'/>"
}) -join "`n"
$html = @"
<!doctype html><html lang='es'><head><meta charset='utf-8'><title>Redirigiendo a Redsys...</title></head>
<body><p>Redirigiendo a la web de pago de Redsys...</p>
<form id='redsys' method='POST' action='$($form.url)'>
$inputs
</form>
<script>document.getElementById('redsys').submit();</script></body></html>
"@

$file = Join-Path $env:TEMP "cinasee-redsys-$ReservationId.html"
Set-Content -Path $file -Value $html -Encoding UTF8
Start-Process $file
Write-Output "Se ha abierto el navegador. Paga con la tarjeta de prueba de Redsys (ver docs/ejecucion-aislada.md)."
