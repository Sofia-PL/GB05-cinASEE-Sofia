package com.cinasee.componente1_cartelera.models;

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

    private Long roomId;

    private LocalDateTime startTime;

    private String status; // PROGRAMADA, A_LA_VENTA, EN_CURSO, FINALIZADA, CANCELADA
}

