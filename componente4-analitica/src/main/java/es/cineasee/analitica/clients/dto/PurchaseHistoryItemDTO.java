package es.cineasee.analitica.clients.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseHistoryItemDTO {
    private Long reservationId;
    private String movieId;
    private LocalDateTime purchaseDate;
    private Double amount;
}

