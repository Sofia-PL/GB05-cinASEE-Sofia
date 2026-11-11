package com.cinasee.componente2_reservas.controllers;

import com.cinasee.componente2_reservas.dto.TicketConfirmation;
import com.cinasee.componente2_reservas.exceptions.PaymentDeclinedException;
import com.cinasee.componente2_reservas.exceptions.ReservationApiException;
import com.cinasee.componente2_reservas.services.RedsysPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.util.stream.Collectors;

/**
 * Página a la que vuelve el navegador del cliente desde la web de Redsys (URL OK / URL KO).
 *
 * <p>Sirve para pagar con Redsys <b>sin depender del frontend</b> (pruebas, demostraciones).
 * Cuando el frontend tenga su propia página de resultado, las URL OK/KO apuntarán allí y
 * esta página dejará de usarse, pero puede seguir activa como respaldo.</p>
 *
 * <p>Es pública (sin JWT) porque la abre el navegador al volver de Redsys. No es un
 * agujero: solo confirma si la respuesta lleva una firma válida de Redsys, que nadie
 * puede generar sin la clave del terminal. Admite GET (parámetros en la URL, lo normal)
 * y POST (formulario).</p>
 */
@RestController
@RequestMapping("/reservations/payments/redsys")
@RequiredArgsConstructor
public class RedsysReturnController {

    private final RedsysPaymentService redsysPaymentService;

    @RequestMapping(path = "/return", method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> paymentReturn(
            @RequestParam(name = "Ds_SignatureVersion", required = false) String signatureVersion,
            @RequestParam(name = "Ds_MerchantParameters", required = false) String merchantParameters,
            @RequestParam(name = "Ds_Signature", required = false) String signature) {

        if (merchantParameters == null || signature == null) {
            // Sin parámetros: el cliente canceló en Redsys o el terminal no los envía
            return page(200, "Pago no completado",
                    "No se ha recibido el resultado del pago. La reserva sigue pendiente: puedes volver a intentarlo antes de que caduque.");
        }
        try {
            TicketConfirmation confirmation =
                    redsysPaymentService.confirmFromRedsys(signatureVersion, merchantParameters, signature);
            String tickets = confirmation.tickets().stream()
                    .map(code -> "<li><code>" + HtmlUtils.htmlEscape(code) + "</code></li>")
                    .collect(Collectors.joining());
            return page(200, "Pago confirmado",
                    "Reserva " + confirmation.reservationId() + " pagada: " + confirmation.totalAmount() + " "
                            + HtmlUtils.htmlEscape(confirmation.currency()) + ".</p><p>Entradas (contenido de los QR):</p><ul>"
                            + tickets + "</ul><p>");
        } catch (PaymentDeclinedException e) {
            return page(402, "Pago rechazado", HtmlUtils.htmlEscape(e.getMessage())
                    + ". La reserva sigue pendiente: puedes intentarlo con otra tarjeta antes de que caduque.");
        } catch (ReservationApiException e) {
            return page(e.getStatus().value(), "No se ha podido confirmar el pago", HtmlUtils.htmlEscape(e.getMessage()));
        }
    }

    /** Página HTML mínima con el resultado. Todo texto variable se escapa. */
    private static ResponseEntity<String> page(int status, String title, String body) {
        String html = "<!doctype html><html lang='es'><head><meta charset='utf-8'><title>CinAsee · " + title
                + "</title></head><body style='font-family:sans-serif;max-width:640px;margin:40px auto'>"
                + "<h1>" + title + "</h1><p>" + body + "</p></body></html>";
        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(html);
    }
}
