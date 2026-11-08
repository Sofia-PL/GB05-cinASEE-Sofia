package com.cinasee.componente2_reservas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Butaca elegida en el mapa interactivo (schema SeatSelection del OpenAPI).
 * Filas y columnas empiezan en 1; el límite superior lo marca la sala (Comp. 1).
 */
public record SeatSelection(
        @NotNull(message = "La fila es obligatoria")
        @Min(value = 1, message = "La fila debe ser mayor o igual que 1")
        Integer row,

        @NotNull(message = "La columna es obligatoria")
        @Min(value = 1, message = "La columna debe ser mayor o igual que 1")
        Integer column
) {
    /** Representación compacta "fila-columna" usada en mensajes y en el código de la entrada. */
    public String label() {
        return row + "-" + column;
    }
}
