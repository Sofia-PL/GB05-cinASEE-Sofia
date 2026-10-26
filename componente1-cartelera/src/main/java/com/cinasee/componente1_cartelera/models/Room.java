package es.cineasee.cartelera.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "rooms")
public class Room {
    @Id
    private Long id;
    private String name;
    private String roomType; // ESTANDAR_2D, DIGITAL_3D, IMAX, VIP, 4DX
    private Integer capacity;
    private Integer rowsCount;
    private Integer columnsCount;
    private Boolean wheelchairAccessible;
    private Double basePrice;
}