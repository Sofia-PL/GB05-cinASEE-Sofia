# Evento asíncrono `PaymentCompleted`

El Componente 2 (Reservas y Pagos) publica este evento cuando un pago se
confirma. El Componente 3 lo consume para:

1. Sumar puntos de fidelización al usuario.
2. Guardar la compra en `purchase_history`.

## Topología RabbitMQ

| Elemento | Valor por defecto | Variable de entorno |
|---|---|---|
| Exchange principal | `payment.exchange` (topic, durable) | `PAYMENT_EXCHANGE` |
| Cola | `user.payment.completed` (durable) | `PAYMENT_COMPLETED_QUEUE` |
| Routing key | `payment.completed` | `PAYMENT_COMPLETED_ROUTING_KEY` |
| Exchange de errores | `payment.exchange.dlx` | `PAYMENT_DLX` |
| Cola de errores (DLQ) | `user.payment.completed.dlq` | `PAYMENT_COMPLETED_DLQ` |
| Routing key de errores | `payment.completed.dlq` | `PAYMENT_COMPLETED_DLQ_ROUTING_KEY` |

La topología se declara automáticamente en `RabbitMQConfig`. Si el Componente 2
también declara el exchange, ambos deben usar los mismos argumentos (durable,
sin auto-delete) o RabbitMQ devolverá `PRECONDITION_FAILED`.

## Contrato JSON

```json
{
  "eventId": "3f2c7a1e-9b1d-4c7a-8f2e-1a2b3c4d5e6f",
  "reservationId": 8901,
  "userId": 42,
  "movieId": 1024,
  "amount": 18.50,
  "currency": "EUR",
  "purchaseDate": "2026-09-20T18:30:00Z",
  "occurredAt": "2026-09-20T18:30:05Z"
}
```

| Campo | Obligatorio | Descripción |
|---|---|---|
| `eventId` | No | Identificador del evento (trazabilidad). |
| `reservationId` | **Sí** | Clave de idempotencia. |
| `userId` | No | `null` en compras de invitado. |
| `movieId` | **Sí** | Película comprada. |
| `amount` | **Sí** | Importe pagado, no negativo. |
| `currency` | No | Moneda (informativo). |
| `purchaseDate` | **Sí** | Fecha/hora del pago. |
| `occurredAt` | No | Fecha/hora de emisión del evento. |

Los campos desconocidos se ignoran (`@JsonIgnoreProperties(ignoreUnknown = true)`),
así que el Componente 2 puede añadir campos sin romper el consumidor.

## Reglas de negocio

### Puntos

```
puntos = floor(amount * LOYALTY_POINTS_PER_EURO)
```

Con la configuración por defecto (`1`):

- `18,50 €` → **18 puntos**
- `18,99 €` → **18 puntos**
- `0,99 €` → **0 puntos**

Se redondea hacia abajo para no regalar puntos por céntimos. El multiplicador
se cambia con `LOYALTY_POINTS_PER_EURO`.

### Idempotencia

Antes de procesar nada se comprueba `purchase_history.reservation_id`, que tiene
una restricción `UNIQUE`. Si RabbitMQ reentrega el mensaje (por un reinicio, un
ack perdido, etc.), el evento se ignora y no se duplican puntos.

### Compras de invitado

Si `userId` es `null`, no se asignan puntos ni se guarda historial: no hay
usuario al que asociarlos. El evento se considera procesado correctamente.

### Usuario inexistente

Si `userId` no existe (por ejemplo, una cuenta eliminada), se registra un
`WARN` y se descarta el evento para no bloquear la cola con un mensaje
venenoso.

### Mensajes inválidos

Si faltan `reservationId`, `movieId`, `amount` o `purchaseDate`, o el importe es
negativo, se lanza `IllegalArgumentException`. Spring AMQP reintenta según:

```yaml
spring.rabbitmq.listener.simple.retry:
  enabled: true
  max-attempts: 3
  initial-interval: 1s
  multiplier: 2
```

Agotados los intentos, el mensaje se rechaza y, como
`default-requeue-rejected: false`, se envía a la DLQ.

## Probar el flujo manualmente

1. Levanta RabbitMQ y abre <http://localhost:15672> (`guest` / `guest`).
2. Comprueba que existen el exchange, la cola y el binding.
3. Publica un mensaje de prueba en el exchange `payment.exchange` con routing key
   `payment.completed`:

```json
{
  "eventId": "prueba-1",
  "reservationId": 999001,
  "userId": 1,
  "movieId": 1024,
  "amount": 18.50,
  "currency": "EUR",
  "purchaseDate": "2026-09-20T18:30:00Z",
  "occurredAt": "2026-09-20T18:30:05Z"
}
```

4. En los logs de la aplicación debe aparecer:

```
Reserva 999001: +18 puntos al usuario 1 (total 18)
```

5. Consulta el perfil con el JWT del usuario:

```http
GET http://localhost:8083/v1/iam/users/1
Authorization: Bearer <token>
```

## Limitaciones actuales

- La clave de idempotencia es `reservationId`; si el Componente 2 reutiliza una
  reserva para varios pagos, habría que usar `eventId`.
- No se valida la moneda ni el importe contra la reserva original; se confía en
  el evento del Componente 2.
- La DLQ no tiene reintento automático ni alerta; su revisión es manual.
- La clave RSA del JWT se genera en cada arranque, así que reiniciar invalida
  los tokens emitidos.
