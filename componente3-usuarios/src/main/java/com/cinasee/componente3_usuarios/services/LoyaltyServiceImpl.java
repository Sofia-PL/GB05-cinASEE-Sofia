package com.cinasee.componente3_usuarios.services;

import com.cinasee.componente3_usuarios.events.PaymentCompletedEvent;
import com.cinasee.componente3_usuarios.models.PurchaseHistory;
import com.cinasee.componente3_usuarios.models.User;
import com.cinasee.componente3_usuarios.repositories.PurchaseHistoryRepository;
import com.cinasee.componente3_usuarios.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Slf4j
public class LoyaltyServiceImpl implements LoyaltyService {

    private final UserRepository userRepository;
    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final int pointsPerEuro;

    public LoyaltyServiceImpl(
            UserRepository userRepository,
            PurchaseHistoryRepository purchaseHistoryRepository,
            @Value("${app.loyalty.points-per-euro:1}") int pointsPerEuro
    ) {
        this.userRepository = userRepository;
        this.purchaseHistoryRepository = purchaseHistoryRepository;
        this.pointsPerEuro = pointsPerEuro;
    }

    @Override
    @Transactional
    public void processPaymentCompleted(PaymentCompletedEvent event) {
        validate(event);

        // Idempotencia: si RabbitMQ reentrega el mensaje, no duplicamos puntos.
        if (purchaseHistoryRepository.existsByReservationId(event.reservationId())) {
            log.info("Evento duplicado ignorado para la reserva {}", event.reservationId());
            return;
        }

        // Compra de invitado: no hay usuario al que abonar puntos.
        if (event.userId() == null) {
            log.info("Reserva {} sin userId (compra de invitado): no se asignan puntos",
                    event.reservationId());
            return;
        }

        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null) {
            // Usuario eliminado o evento inconsistente: se descarta para no
            // bloquear la cola con un mensaje venenoso.
            log.warn("Reserva {} asociada al usuario inexistente {}: se ignora",
                    event.reservationId(), event.userId());
            return;
        }

        int earnedPoints = calculatePoints(event.amount());
        user.setLoyaltyPoints(Math.addExact(user.getLoyaltyPoints(), earnedPoints));
        userRepository.save(user);

        PurchaseHistory history = PurchaseHistory.builder()
                .userId(user.getId())
                .reservationId(event.reservationId())
                .movieId(event.movieId())
                .purchaseDate(event.purchaseDate())
                .amount(event.amount())
                .build();
        purchaseHistoryRepository.save(history);

        log.info("Reserva {}: +{} puntos al usuario {} (total {})",
                event.reservationId(), earnedPoints, user.getId(), user.getLoyaltyPoints());
    }

    /**
     * 1 punto por euro completo: 18,99 € -> 18 puntos.
     * Se redondea hacia abajo para no regalar puntos por céntimos.
     */
    private int calculatePoints(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(pointsPerEuro))
                .setScale(0, RoundingMode.FLOOR)
                .intValueExact();
    }

    private void validate(PaymentCompletedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("El evento PaymentCompleted no puede ser nulo");
        }
        if (event.reservationId() == null) {
            throw new IllegalArgumentException("El evento no incluye reservationId");
        }
        if (event.movieId() == null) {
            throw new IllegalArgumentException("El evento no incluye movieId");
        }
        if (event.amount() == null) {
            throw new IllegalArgumentException("El evento no incluye amount");
        }
        if (event.amount().signum() < 0) {
            throw new IllegalArgumentException("El importe de la reserva no puede ser negativo");
        }
        if (event.purchaseDate() == null) {
            throw new IllegalArgumentException("El evento no incluye purchaseDate");
        }
    }
}
