package es.cineasee.analitica.clients.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomDTO {
    private String id;
    private String cinemaId;
    private String name;
    private String roomType;
    private Integer capacity;
    private Integer rowsCount;
    private Integer columnsCount;
    private Boolean wheelchairAccessible;
    private Double basePrice;
}

