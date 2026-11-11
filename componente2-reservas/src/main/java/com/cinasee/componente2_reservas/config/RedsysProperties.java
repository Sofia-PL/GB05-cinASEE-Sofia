package com.cinasee.componente2_reservas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del TPV Virtual de Redsys (prefijo {@code app.payment.redsys}).
 *
 * <p>Los valores se definen en {@code application.yaml}. Por defecto son los
 * <b>datos genéricos de prueba que Redsys publica para todos los desarrolladores</b>
 * (ver el comentario de ese fichero). Para un comercio real se sobrescriben con
 * variables de entorno: nunca se escribe una clave real en el código ni en el
 * repositorio.</p>
 *
 * @param paymentUrl   URL del formulario de pago (entorno de pruebas: sis-t.redsys.es)
 * @param merchantCode número de comercio (FUC)
 * @param terminal     número de terminal
 * @param secretKey    clave secreta del terminal en Base64 (firma HMAC_SHA256_V1)
 * @param currency     moneda en código ISO 4217 numérico (978 = euro)
 * @param urlOk        a dónde vuelve el navegador del cliente tras un pago correcto
 * @param urlKo        a dónde vuelve el navegador del cliente tras un pago fallido o cancelado
 * @param merchantUrl  URL de notificación servidor a servidor (vacía = no se usa; necesita
 *                     una URL pública en internet)
 */
@ConfigurationProperties(prefix = "app.payment.redsys")
public record RedsysProperties(
        String paymentUrl,
        String merchantCode,
        String terminal,
        String secretKey,
        @DefaultValue("978") String currency,
        String urlOk,
        String urlKo,
        @DefaultValue("") String merchantUrl
) {
    /** true si hay clave configurada; sin ella no se puede firmar ningún pago. */
    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank()
                && merchantCode != null && !merchantCode.isBlank()
                && paymentUrl != null && !paymentUrl.isBlank();
    }
}
