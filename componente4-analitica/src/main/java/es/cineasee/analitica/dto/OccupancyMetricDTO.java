package es.cineasee.analitica.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OccupancyMetricDTO {
    private String cinemaId;
    private String screenId;
    private Integer dayOfWeek;
    private String timeRange;
    private Integer totalCapacity;
    private Integer totalTicketsSold;
    private Double occupancyPercentage;
}

