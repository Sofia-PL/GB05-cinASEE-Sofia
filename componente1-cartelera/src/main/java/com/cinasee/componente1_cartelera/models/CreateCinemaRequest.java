package com.cinasee.componente1_cartelera.models;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCinemaRequest {

    @NotBlank(message = "El nombre del cine es obligatorio")
    private String name;

    @NotBlank(message = "La dirección del cine es obligatoria")
    private String address;

    @NotBlank(message = "La ciudad del cine es obligatoria")
    private String city;

    private String phone;
}
