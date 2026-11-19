package com.cinasee.componente3_usuarios.events;

import com.cinasee.componente3_usuarios.services.LoyaltyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCompletedListenerTest {

    @Mock
    private LoyaltyService loyaltyService;

    @InjectMocks
    private PaymentCompletedListener listener;

    @Test
    void delegatesEventToLoyaltyService() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                "evt-8901",
                8901L,
                42L,
                "movie-1024",
                new BigDecimal("18.50"),
                "EUR",
                OffsetDateTime.parse("2026-09-20T18:30:00Z"),
                OffsetDateTime.parse("2026-09-20T18:30:05Z")
        );

        listener.onPaymentCompleted(event);

        verify(loyaltyService).processPaymentCompleted(event);
    }
}
