package es.cineasee.analitica.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoxOfficeReportDTO {
    private Long distributorId;
    private String movieId;
    private String movieTitle;
    private PeriodDTO period;
    private Long totalTicketsSold;
    private Double grossRevenue;
    private String currency;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodDTO {
        private LocalDate startDate;
        private LocalDate endDate;
    }
}

