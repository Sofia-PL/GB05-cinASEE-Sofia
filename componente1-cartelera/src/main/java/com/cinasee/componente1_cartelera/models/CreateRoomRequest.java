package com.cinasee.componente1_cartelera.models;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {

    @NotNull(message = "El ID del cine es obligatorio")
    private String cinemaId;

    @NotBlank(message = "El nombre de la sala es obligatorio")
    private String name;

    @NotBlank(message = "El tipo de sala es obligatorio")
    private String roomType;

    @NotNull(message = "El número de filas es obligatorio")
    @Min(value = 1, message = "Al menos 1 fila")
    private Integer rowsCount;

    @NotNull(message = "El número de columnas es obligatorio")
    @Min(value = 1, message = "Al menos 1 columna")
    private Integer columnsCount;

    private Boolean wheelchairAccessible;

    @Min(value = 0, message = "El precio base no puede ser negativo")
    private Double basePrice;
}

