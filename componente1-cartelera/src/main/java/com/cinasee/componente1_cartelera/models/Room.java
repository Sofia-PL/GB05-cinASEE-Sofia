package com.cinasee.componente1_cartelera.models;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rooms")
public class Room {
    @Id
    private String id;
    private String cinemaId;
    private String name;
    private String roomType; // ESTANDAR_2D, DIGITAL_3D, IMAX, VIP, 4DX
    private Integer capacity;
    
    @Min(1) // Al menos 1 fila
    private Integer rowsCount;
    
    @Min(1) // Al menos 1 columna
    private Integer columnsCount;
    
    private Boolean wheelchairAccessible;
    
    @Min(0) // Precio no negativo
    private Double basePrice;
}

