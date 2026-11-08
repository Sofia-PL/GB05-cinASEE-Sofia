package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.clients.dto.CatalogRoom;
import com.cinasee.componente2_reservas.clients.dto.CatalogSession;
import com.cinasee.componente2_reservas.clients.dto.UserProfileDto;
import com.cinasee.componente2_reservas.config.PricingProperties;
import com.cinasee.componente2_reservas.exceptions.InvalidStateException;
import com.cinasee.componente2_reservas.models.DiscountType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Motor de tarifas del Componente 2.
 *
 * <p><b>Precio base:</b> el {@code ticketPrice} que Cartelera fija para cada sesión.
 * Si la sesión no lo trae, se usa el {@code basePrice} de la sala.</p>
 *
 * <p><b>Descuentos</b> (configurables en {@code app.pricing}):</p>
 * <ul>
 *   <li><b>Día del espectador</b>: 20 % si la sesión es en miércoles (hora local del cine).</li>
 *   <li><b>Fidelización</b>: 10 % si el usuario registrado tiene al menos 500 puntos en su
 *       monedero (dato que se consulta síncronamente al Componente 3).</li>
 * </ul>
 * <p>Los descuentos NO se acumulan: se aplica el mayor. El descuento de senior que
 * menciona la memoria no se aplica porque el Componente 3 no expone la edad del usuario.</p>
 *
 * <p>Se trabaja siempre con {@link BigDecimal} (nunca double) para no perder
 * céntimos por errores de redondeo binario.</p>
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingProperties properties;

    /**
     * @param session       sesión de Cartelera
     * @param room          sala de Cartelera (precio de respaldo)
     * @param user          perfil del Componente 3, o {@code null} si es una compra de invitado
     * @param showStartTime hora de inicio de la sesión
     * @param seatCount     número de butacas
     */
    public PriceQuote quote(CatalogSession session, CatalogRoom room, UserProfileDto user,
                            Instant showStartTime, int seatCount) {
        BigDecimal basePrice = resolveBasePrice(session, room);

        DiscountType discount = DiscountType.NONE;
        BigDecimal rate = BigDecimal.ZERO;

        // Día del espectador: depende del día de la semana en la zona horaria del cine
        if (showStartTime.atZone(properties.timeZone()).getDayOfWeek() == properties.spectatorDay()
                && properties.spectatorDayDiscount().compareTo(rate) > 0) {
            discount = DiscountType.SPECTATOR_DAY;
            rate = properties.spectatorDayDiscount();
        }

        // Fidelización: solo si hay usuario registrado con puntos suficientes
        if (user != null && user.loyaltyPoints() != null
                && user.loyaltyPoints() >= properties.loyaltyPointsThreshold()
                && properties.loyaltyDiscount().compareTo(rate) > 0) {
            discount = DiscountType.LOYALTY;
            rate = properties.loyaltyDiscount();
        }

        BigDecimal unitPrice = basePrice
                .multiply(BigDecimal.ONE.subtract(rate))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(seatCount)).setScale(2, RoundingMode.HALF_UP);

        return new PriceQuote(basePrice.setScale(2, RoundingMode.HALF_UP), discount, rate, unitPrice, total);
    }

    private BigDecimal resolveBasePrice(CatalogSession session, CatalogRoom room) {
        BigDecimal price = session.ticketPrice() != null ? session.ticketPrice() : room.basePrice();
        if (price == null || price.signum() < 0) {
            // Dato incoherente en Cartelera: no se puede vender sin precio
            throw new InvalidStateException("SHOW_WITHOUT_PRICE",
                    "La sesión " + session.id() + " no tiene un precio válido configurado en Cartelera");
        }
        return price;
    }
}
