package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.config.RedsysProperties;
import com.cinasee.componente2_reservas.config.ReservationProperties;
import com.cinasee.componente2_reservas.dto.RedsysPaymentForm;
import com.cinasee.componente2_reservas.dto.RedsysResultRequest;
import com.cinasee.componente2_reservas.dto.TicketConfirmation;
import com.cinasee.componente2_reservas.exceptions.InvalidPaymentResponseException;
import com.cinasee.componente2_reservas.exceptions.InvalidStateException;
import com.cinasee.componente2_reservas.exceptions.PaymentDeclinedException;
import com.cinasee.componente2_reservas.exceptions.ReservationExpiredException;
import com.cinasee.componente2_reservas.exceptions.ResourceNotFoundException;
import com.cinasee.componente2_reservas.exceptions.ServiceUnavailableException;
import com.cinasee.componente2_reservas.models.Reservation;
import com.cinasee.componente2_reservas.models.ReservationStatus;
import com.cinasee.componente2_reservas.repositories.ReservationRepository;
import com.cinasee.componente2_reservas.security.CallerIdentity;
import com.cinasee.componente2_reservas.services.payment.RedsysSignature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pago con el TPV Virtual de Redsys por <b>redirección</b> (activo con {@code app.payment.mode=redsys}).
 *
 * <pre>
 * 1. startPayment      Reservas firma el pedido y devuelve los datos del formulario.
 * 2. (navegador)       El cliente va a la web de Redsys, mete la tarjeta y pasa el 3D Secure.
 * 3. (navegador)       Redsys lo devuelve a la URL OK / KO con el resultado FIRMADO.
 * 4. confirmPayment    Reservas valida la firma, el pedido y el importe y, si Redsys
 *                      autorizó el pago (Ds_Response 0000-0099), confirma la reserva:
 *                      entradas QR + evento PaymentCompleted (igual que con la pasarela simulada).
 * </pre>
 *
 * <p>Seguridad: Reservas nunca ve la tarjeta. La respuesta se da por buena solo si su
 * firma HMAC-SHA256 coincide con la que calculamos con la clave del terminal, el pedido
 * es el último que enviamos para esa reserva y el importe y el comercio coinciden. Así
 * nadie puede "confirmar" una reserva inventándose o modificando una respuesta.</p>
 *
 * <p>Mientras el cliente está en la web de Redsys, la reserva no caduca aunque pasen
 * sus 10 minutos, durante {@code app.reservations.payment-grace} (15 min por defecto).</p>
 */
@Slf4j
@Service
public class RedsysPaymentService {

    /** Ds_Merchant_TransactionType "0" = autorización (pago normal). */
    private static final String TRANSACTION_AUTHORIZATION = "0";

    private final ReservationRepository reservationRepository;
    private final SeatLockService seatLockService;
    private final PaymentService paymentService;
    private final RedsysProperties redsys;
    private final ReservationProperties reservationProperties;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final String paymentMode;

    public RedsysPaymentService(ReservationRepository reservationRepository,
                                SeatLockService seatLockService,
                                PaymentService paymentService,
                                RedsysProperties redsys,
                                ReservationProperties reservationProperties,
                                JsonMapper jsonMapper,
                                Clock clock,
                                @Value("${app.payment.mode:simulated}") String paymentMode) {
        this.reservationRepository = reservationRepository;
        this.seatLockService = seatLockService;
        this.paymentService = paymentService;
        this.redsys = redsys;
        this.reservationProperties = reservationProperties;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.paymentMode = paymentMode;
    }

    // ------------------------------------------------------------------
    // Paso 1: iniciar el pago
    // ------------------------------------------------------------------

    /**
     * Prepara el formulario firmado para enviar al cliente a la web de Redsys.
     * La reserva sigue PENDING_PAYMENT; se anota el pedido y el inicio del pago.
     */
    @Transactional(noRollbackFor = ReservationExpiredException.class)
    public RedsysPaymentForm startPayment(Long reservationId, CallerIdentity caller) {
        ensureRedsysMode();
        RedsysSignature signer = signer();

        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("La reserva " + reservationId + " no existe"));
        ReservationAccessPolicy.checkCanAccess(reservation, caller);

        Instant now = clock.instant();
        ensureCanStartPayment(reservation, now);

        // Un número de pedido nuevo por intento: Redsys rechaza los pedidos repetidos
        String order = PaymentService.buildOrderId(reservation.getId(), now);
        if (order.equals(reservation.getPaymentOrder())) {
            order = PaymentService.buildOrderId(reservation.getId(), now.plusMillis(1));
        }

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("DS_MERCHANT_AMOUNT", toCents(reservation.getTotalAmount()));
        parameters.put("DS_MERCHANT_ORDER", order);
        parameters.put("DS_MERCHANT_MERCHANTCODE", redsys.merchantCode());
        parameters.put("DS_MERCHANT_CURRENCY", redsys.currency());
        parameters.put("DS_MERCHANT_TRANSACTIONTYPE", TRANSACTION_AUTHORIZATION);
        parameters.put("DS_MERCHANT_TERMINAL", redsys.terminal());
        parameters.put("DS_MERCHANT_URLOK", redsys.urlOk());
        parameters.put("DS_MERCHANT_URLKO", redsys.urlKo());
        if (redsys.merchantUrl() != null && !redsys.merchantUrl().isBlank()) {
            parameters.put("DS_MERCHANT_MERCHANTURL", redsys.merchantUrl());
        }
        parameters.put("DS_MERCHANT_PRODUCTDESCRIPTION", "Entradas CinAsee - reserva " + reservation.getId());

        // Ds_MerchantParameters = JSON en Base64 (sin saltos de línea); Ds_Signature = HMAC sobre ese texto
        String merchantParameters = Base64.getEncoder()
                .encodeToString(jsonMapper.writeValueAsString(parameters).getBytes(StandardCharsets.UTF_8));
        String signature = signer.sign(order, merchantParameters);

        reservation.setPaymentOrder(order);
        reservation.setPaymentStartedAt(now);
        reservation.setPaymentGateway("REDSYS");

        Instant graceEnd = now.plus(reservationProperties.paymentGrace());
        Instant deadline = graceEnd.isAfter(reservation.getExpiresAt()) ? graceEnd : reservation.getExpiresAt();

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Ds_SignatureVersion", RedsysSignature.VERSION);
        fields.put("Ds_MerchantParameters", merchantParameters);
        fields.put("Ds_Signature", signature);

        log.info("Reserva {}: pago Redsys iniciado (pedido {}, {} {})",
                reservation.getId(), order, reservation.getTotalAmount(), reservation.getCurrency());
        return new RedsysPaymentForm(reservation.getId(), redsys.paymentUrl(), fields, order,
                reservation.getTotalAmount(), reservation.getCurrency(), deadline);
    }

    // ------------------------------------------------------------------
    // Paso 4: confirmar con la respuesta firmada de Redsys
    // ------------------------------------------------------------------

    /**
     * Confirmación enviada por el frontend (con el JWT del cliente) tras volver de Redsys.
     * Además de la firma, comprueba que la respuesta es de ESTA reserva y que quien
     * llama puede actuar sobre ella.
     */
    @Transactional(noRollbackFor = PaymentDeclinedException.class)
    public TicketConfirmation confirmPayment(Long reservationId, RedsysResultRequest result, CallerIdentity caller) {
        ensureRedsysMode();
        RedsysResponse response = verify(result.signatureVersion(), result.merchantParameters(), result.signature());
        Reservation reservation = findByOrder(response.order());
        if (!reservation.getId().equals(reservationId)) {
            throw new InvalidPaymentResponseException("La respuesta de Redsys no corresponde a la reserva " + reservationId);
        }
        ReservationAccessPolicy.checkCanAccess(reservation, caller);
        return apply(reservation, response);
    }

    /**
     * Confirmación desde la vuelta directa del navegador a Reservas (URL OK / KO apuntando
     * a este microservicio). No hay JWT: la autenticidad la garantiza la firma de Redsys,
     * que solo puede generar quien tiene la clave del terminal.
     */
    @Transactional(noRollbackFor = PaymentDeclinedException.class)
    public TicketConfirmation confirmFromRedsys(String signatureVersion, String merchantParameters, String signature) {
        ensureRedsysMode();
        RedsysResponse response = verify(signatureVersion, merchantParameters, signature);
        return apply(findByOrder(response.order()), response);
    }

    /** Aplica a la reserva una respuesta de Redsys ya verificada. */
    private TicketConfirmation apply(Reservation reservation, RedsysResponse response) {
        // El importe, la moneda y el comercio deben ser exactamente los que enviamos
        if (!toCents(reservation.getTotalAmount()).equals(stripLeadingZeros(response.amount()))) {
            throw new InvalidPaymentResponseException("El importe de la respuesta de Redsys no coincide con el de la reserva");
        }
        if (response.currency() != null && !redsys.currency().equals(response.currency())) {
            throw new InvalidPaymentResponseException("La moneda de la respuesta de Redsys no coincide");
        }
        if (response.merchantCode() != null && !redsys.merchantCode().equals(response.merchantCode())) {
            throw new InvalidPaymentResponseException("El comercio de la respuesta de Redsys no coincide");
        }

        // Respuesta repetida (el cliente recarga la página, o llegan el retorno y la confirmación):
        // no se vuelve a emitir nada, se devuelve lo que ya había
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            if (response.order().equals(reservation.getPaymentReference())) {
                return PaymentService.toConfirmation(reservation);
            }
            throw new InvalidStateException("ALREADY_PAID", "La reserva " + reservation.getId() + " ya está pagada");
        }

        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            if (response.approved()) {
                // Caso excepcional: Redsys cobró pero la reserva ya estaba cerrada (caducó tras el margen)
                log.error("Redsys autorizó el pedido {} de la reserva {} que está {}: requiere devolución manual",
                        response.order(), reservation.getId(), reservation.getStatus());
            }
            throw new InvalidStateException("RESERVATION_CLOSED",
                    "La reserva " + reservation.getId() + " está " + reservation.getStatus() + " y no se puede confirmar");
        }

        if (!response.order().equals(reservation.getPaymentOrder())) {
            // Un intento anterior que llega tarde: solo vale el último pedido enviado
            throw new InvalidPaymentResponseException("El pedido " + response.order() + " no es el último intento de pago de la reserva");
        }

        Instant now = clock.instant();
        if (response.approved()) {
            return paymentService.confirmPaidReservation(reservation, "REDSYS", response.order(), now);
        }

        // Pago rechazado: la reserva sigue pendiente y vuelve a contar solo su TTL normal
        reservation.setPaymentStartedAt(null);
        log.info("Redsys rechazó el pedido {} de la reserva {} (Ds_Response {})",
                response.order(), reservation.getId(), response.responseCode());
        throw new PaymentDeclinedException("Redsys ha denegado la operación (código " + response.responseCode() + ")");
    }

    // ------------------------------------------------------------------
    // Validaciones y utilidades
    // ------------------------------------------------------------------

    private void ensureRedsysMode() {
        if (!"redsys".equalsIgnoreCase(paymentMode)) {
            throw new InvalidStateException("PAYMENT_MODE_MISMATCH",
                    "El pago con Redsys no está activo (app.payment.mode=" + paymentMode + ")");
        }
    }

    private RedsysSignature signer() {
        if (!redsys.isConfigured()) {
            throw new ServiceUnavailableException(
                    "La pasarela Redsys no está configurada (faltan app.payment.redsys.secret-key, merchant-code o payment-url)", null);
        }
        return new RedsysSignature(redsys.secretKey());
    }

    /** Solo se puede iniciar un pago sobre una reserva pendiente y dentro de su plazo. */
    private void ensureCanStartPayment(Reservation reservation, Instant now) {
        switch (reservation.getStatus()) {
            case CONFIRMED -> throw new InvalidStateException("ALREADY_PAID",
                    "La reserva " + reservation.getId() + " ya está pagada");
            case EXPIRED -> throw new ReservationExpiredException(reservation.getId());
            case CANCELLED, REFUNDED -> throw new InvalidStateException("RESERVATION_CLOSED",
                    "La reserva " + reservation.getId() + " está " + reservation.getStatus() + " y no se puede pagar");
            case PENDING_PAYMENT -> {
                boolean expired = reservation.isHoldExpiredAt(now)
                        && !reservation.isPaymentInProgressAt(now, reservationProperties.paymentGrace());
                if (expired) {
                    seatLockService.release(List.of(reservation), ReservationStatus.EXPIRED, now);
                    throw new ReservationExpiredException(reservation.getId());
                }
            }
        }
    }

    private Reservation findByOrder(String order) {
        return reservationRepository.findByPaymentOrderForUpdate(order)
                .orElseThrow(() -> new ResourceNotFoundException("No hay ninguna reserva con el pedido de Redsys " + order));
    }

    /** Comprueba versión y firma, y extrae los datos de la respuesta. */
    private RedsysResponse verify(String signatureVersion, String merchantParameters, String signature) {
        if (!RedsysSignature.VERSION.equals(signatureVersion)) {
            throw new InvalidPaymentResponseException("Versión de firma de Redsys no admitida: " + signatureVersion);
        }
        if (merchantParameters == null || merchantParameters.isBlank() || signature == null || signature.isBlank()) {
            throw new InvalidPaymentResponseException("Faltan los parámetros firmados de Redsys");
        }

        Map<String, Object> data;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = jsonMapper.readValue(RedsysSignature.decodeAnyBase64(merchantParameters), Map.class);
            data = parsed;
        } catch (RuntimeException e) {
            throw new InvalidPaymentResponseException("Los parámetros de Redsys no son un JSON en Base64 válido");
        }

        String order = field(data, "Ds_Order");
        if (order == null) {
            throw new InvalidPaymentResponseException("La respuesta de Redsys no incluye el número de pedido");
        }
        if (!signer().verify(order, merchantParameters, signature)) {
            log.warn("Respuesta de Redsys con firma incorrecta para el pedido {}", order);
            throw new InvalidPaymentResponseException("La firma de la respuesta de Redsys no es válida");
        }
        return new RedsysResponse(order, field(data, "Ds_Amount"), field(data, "Ds_Currency"),
                field(data, "Ds_MerchantCode"), field(data, "Ds_Response"));
    }

    /** Lee un campo de la respuesta sin distinguir mayúsculas (Redsys usa Ds_Order o DS_ORDER). */
    private static String field(Map<String, Object> data, String name) {
        return data.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name) && e.getValue() != null)
                .map(e -> e.getValue().toString().trim())
                .findFirst().orElse(null);
    }

    /** 15.30 → "1530": Redsys trabaja en céntimos, sin decimales. */
    static String toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String stripLeadingZeros(String value) {
        if (value == null) {
            return "";
        }
        String stripped = value.replaceFirst("^0+(?=\\d)", "");
        return stripped;
    }

    /**
     * Datos útiles de la respuesta de Redsys.
     *
     * @param responseCode Ds_Response: de 0000 a 0099 = pago autorizado (manual de Redsys)
     */
    record RedsysResponse(String order, String amount, String currency, String merchantCode, String responseCode) {
        boolean approved() {
            try {
                int code = Integer.parseInt(responseCode);
                return code >= 0 && code <= 99;
            } catch (NumberFormatException | NullPointerException e) {
                return false;
            }
        }
    }
}
