package com.cinasee.componente2_reservas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de POST /reservations/{id}/payment/redsys/confirm.
 *
 * Son los tres parámetros que Redsys añade a la URL de vuelta (URL OK / URL KO).
 * Se usan sus nombres originales para que el frontend los reenvíe tal cual los recibe.
 * La firma garantiza que nadie los ha modificado por el camino.
 */
public record RedsysResultRequest(
        @JsonProperty("Ds_SignatureVersion")
        @NotBlank(message = "Ds_SignatureVersion es obligatorio")
        String signatureVersion,

        @JsonProperty("Ds_MerchantParameters")
        @NotBlank(message = "Ds_MerchantParameters es obligatorio")
        String merchantParameters,

        @JsonProperty("Ds_Signature")
        @NotBlank(message = "Ds_Signature es obligatorio")
        String signature
) {
}
