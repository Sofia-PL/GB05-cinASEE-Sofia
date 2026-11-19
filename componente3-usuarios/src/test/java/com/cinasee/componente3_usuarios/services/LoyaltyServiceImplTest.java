package com.cinasee.componente3_usuarios.services;

import com.cinasee.componente3_usuarios.events.PaymentCompletedEvent;
import com.cinasee.componente3_usuarios.models.PurchaseHistory;
import com.cinasee.componente3_usuarios.models.Role;
import com.cinasee.componente3_usuarios.models.User;
import com.cinasee.componente3_usuarios.repositories.PurchaseHistoryRepository;
import com.cinasee.componente3_usuarios.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoyaltyServiceImplTest {

    private static final OffsetDateTime PURCHASE_DATE =
            OffsetDateTime.parse("2026-09-20T18:30:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PurchaseHistoryRepository purchaseHistoryRepository;

    private LoyaltyServiceImpl loyaltyService;

    @BeforeEach
    void setUp() {
        loyaltyService = new LoyaltyServiceImpl(userRepository, purchaseHistoryRepository, 1);
    }

    @Test
    void newPaymentAddsPointsAndRegistersHistory() {
        PaymentCompletedEvent event = event(42L, "18.50");
        when(purchaseHistoryRepository.existsByReservationId(8901L)).thenReturn(false);
        User user = user(42L, 100);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        loyaltyService.processPaymentCompleted(event);

        assertEquals(118, user.getLoyaltyPoints());
        verify(userRepository).save(user);

        ArgumentCaptor<PurchaseHistory> captor = ArgumentCaptor.forClass(PurchaseHistory.class);
        verify(purchaseHistoryRepository).save(captor.capture());

        PurchaseHistory saved = captor.getValue();
        assertEquals(42L, saved.getUserId());
        assertEquals(8901L, saved.getReservationId());
        assertEquals("movie-1024", saved.getMovieId());
        assertEquals(new BigDecimal("18.50"), saved.getAmount());
        assertEquals(PURCHASE_DATE, saved.getPurchaseDate());
    }

    @Test
    void pointsAreRoundedDownToCompleteEuros() {
        PaymentCompletedEvent event = event(42L, "18.99");
        when(purchaseHistoryRepository.existsByReservationId(8901L)).thenReturn(false);
        User user = user(42L, 0);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        loyaltyService.processPaymentCompleted(event);

        assertEquals(18, user.getLoyaltyPoints());
    }

    @Test
    void pointsPerEuroIsConfigurable() {
        loyaltyService = new LoyaltyServiceImpl(userRepository, purchaseHistoryRepository, 2);
        PaymentCompletedEvent event = event(42L, "18.99");
        when(purchaseHistoryRepository.existsByReservationId(8901L)).thenReturn(false);
        User user = user(42L, 0);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        loyaltyService.processPaymentCompleted(event);

        assertEquals(37, user.getLoyaltyPoints());
    }

    @Test
    void duplicateEventIsIgnored() {
        when(purchaseHistoryRepository.existsByReservationId(8901L)).thenReturn(true);

        loyaltyService.processPaymentCompleted(event(42L, "18.50"));

        verifyNoInteractions(userRepository);
        verify(purchaseHistoryRepository, never()).save(any());
    }

    @Test
    void guestPurchaseWithoutUserIsIgnored() {
        when(purchaseHistoryRepository.existsByReservationId(8901L)).thenReturn(false);

        loyaltyService.processPaymentCompleted(event(null, "18.50"));

        verifyNoInteractions(userRepository);
        verify(purchaseHistoryRepository, never()).save(any());
    }

    @Test
    void unknownUserIsIgnored() {
        when(purchaseHistoryRepository.existsByReservationId(8901L)).thenReturn(false);
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        loyaltyService.processPaymentCompleted(event(42L, "18.50"));

        verify(userRepository, never()).save(any());
        verify(purchaseHistoryRepository, never()).save(any());
    }

    @Test
    void negativeAmountIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> loyaltyService.processPaymentCompleted(event(42L, "-1.00"))
        );

        verifyNoInteractions(userRepository, purchaseHistoryRepository);
    }

    @Test
    void eventWithoutReservationIdIsRejected() {
        PaymentCompletedEvent invalid = new PaymentCompletedEvent(
                "evt-1", null, 42L, "movie-1024", new BigDecimal("18.50"),
                "EUR", PURCHASE_DATE, PURCHASE_DATE
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> loyaltyService.processPaymentCompleted(invalid)
        );

        verifyNoInteractions(userRepository, purchaseHistoryRepository);
    }

    @Test
    void eventWithoutPurchaseDateIsRejected() {
        PaymentCompletedEvent invalid = new PaymentCompletedEvent(
                "evt-1", 8901L, 42L, "movie-1024", new BigDecimal("18.50"),
                "EUR", null, PURCHASE_DATE
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> loyaltyService.processPaymentCompleted(invalid)
        );

        verifyNoInteractions(userRepository, purchaseHistoryRepository);
    }

    private PaymentCompletedEvent event(Long userId, String amount) {
        return new PaymentCompletedEvent(
                "evt-8901",
                8901L,
                userId,
                "movie-1024",
                new BigDecimal(amount),
                "EUR",
                PURCHASE_DATE,
                PURCHASE_DATE
        );
    }

    private User user(Long id, int points) {
        return User.builder()
                .id(id)
                .email("cliente" + id + "@cinasee.com")
                .passwordHash("hashed")
                .fullName("Cliente " + id)
                .role(Role.CLIENTE_REGISTRADO)
                .loyaltyPoints(points)
                .build();
    }
}
