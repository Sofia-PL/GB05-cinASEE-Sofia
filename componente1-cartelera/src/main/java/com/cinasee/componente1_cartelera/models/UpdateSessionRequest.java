package com.cinasee.componente1_cartelera.models;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSessionRequest {

    private String roomId;

    private LocalDateTime startTime;

    private String status; // PROGRAMADA, A_LA_VENTA, EN_CURSO, FINALIZADA, CANCELADA

    /** Nuevo precio de la entrada (opcional). Las reservas ya hechas conservan su precio. */
    @Min(value = 0, message = "El precio de la entrada no puede ser negativo")
    private Double ticketPrice;
}

