package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.TestData;
import com.cinasee.componente2_reservas.clients.dto.CatalogSession;
import com.cinasee.componente2_reservas.exceptions.InvalidStateException;
import com.cinasee.componente2_reservas.models.DiscountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitarios del motor de tarifas (sin Spring, sin base de datos).
 */
class PricingServiceTest {

    private final PricingService pricingService = new PricingService(TestData.pricingProperties());

    private static Instant at(String localUtc) {
        return CatalogTime.toInstant(localUtc, ZoneOffset.UTC);
    }

    @Test
    void sinDescuento_cobraElPrecioDeLaSesionPorButaca() {
        PriceQuote quote = pricingService.quote(TestData.sessionOnSale(), TestData.room(), null,
                at(TestData.FRIDAY_SHOW), 2);

        assertThat(quote.discountType()).isEqualTo(DiscountType.NONE);
        assertThat(quote.unitPrice()).isEqualByComparingTo("8.50");
        assertThat(quote.totalAmount()).isEqualByComparingTo("17.00");
    }

    @Test
    void diaDelEspectador_aplica20PorCientoEnMiercoles() {
        CatalogSession wednesday = TestData.session("session-1", "A_LA_VENTA", TestData.WEDNESDAY_SHOW, new BigDecimal("8.50"));

        PriceQuote quote = pricingService.quote(wednesday, TestData.room(), null, at(TestData.WEDNESDAY_SHOW), 2);

        assertThat(quote.discountType()).isEqualTo(DiscountType.SPECTATOR_DAY);
        assertThat(quote.unitPrice()).isEqualByComparingTo("6.80"); // 8,50 x 0,80
        assertThat(quote.totalAmount()).isEqualByComparingTo("13.60");
    }

    @Test
    void diaDelEspectador_usaLaHoraLocalDelCine() {
        // Martes 23:30 UTC = miércoles 01:30 en Madrid (horario de verano) → hay descuento
        Instant tuesdayUtcWednesdayMadrid = Instant.parse("2026-10-13T23:30:00Z");

        PriceQuote quote = pricingService.quote(TestData.sessionOnSale(), TestData.room(), null,
                tuesdayUtcWednesdayMadrid, 1);

        assertThat(quote.discountType()).isEqualTo(DiscountType.SPECTATOR_DAY);
    }

    @Test
    void fidelizacion_aplica10PorCientoConPuntosSuficientes() {
        PriceQuote quote = pricingService.quote(TestData.sessionOnSale(), TestData.room(), TestData.user(1250),
                at(TestData.FRIDAY_SHOW), 2);

        assertThat(quote.discountType()).isEqualTo(DiscountType.LOYALTY);
        assertThat(quote.unitPrice()).isEqualByComparingTo("7.65"); // 8,50 x 0,90
        assertThat(quote.totalAmount()).isEqualByComparingTo("15.30");
    }

    @Test
    void fidelizacion_noSeAplicaPorDebajoDelUmbral() {
        PriceQuote quote = pricingService.quote(TestData.sessionOnSale(), TestData.room(), TestData.user(499),
                at(TestData.FRIDAY_SHOW), 1);

        assertThat(quote.discountType()).isEqualTo(DiscountType.NONE);
    }

    @Test
    void descuentosNoAcumulables_seAplicaElMayor() {
        CatalogSession wednesday = TestData.session("session-1", "A_LA_VENTA", TestData.WEDNESDAY_SHOW, new BigDecimal("10.00"));

        PriceQuote quote = pricingService.quote(wednesday, TestData.room(), TestData.user(5000),
                at(TestData.WEDNESDAY_SHOW), 1);

        // 20 % (espectador) > 10 % (fidelización)
        assertThat(quote.discountType()).isEqualTo(DiscountType.SPECTATOR_DAY);
        assertThat(quote.unitPrice()).isEqualByComparingTo("8.00");
    }

    @Test
    void sinPrecioEnLaSesion_usaElPrecioBaseDeLaSala() {
        CatalogSession noPrice = TestData.session("session-1", "A_LA_VENTA", TestData.FRIDAY_SHOW, null);

        PriceQuote quote = pricingService.quote(noPrice, TestData.room(), null, at(TestData.FRIDAY_SHOW), 1);

        assertThat(quote.unitPrice()).isEqualByComparingTo("7.50");
    }

    @Test
    void redondeoHalfUpACentimos() {
        CatalogSession odd = TestData.session("session-1", "A_LA_VENTA", TestData.WEDNESDAY_SHOW, new BigDecimal("8.33"));

        PriceQuote quote = pricingService.quote(odd, TestData.room(), null, at(TestData.WEDNESDAY_SHOW), 3);

        // 8,33 x 0,80 = 6,664 → 6,66 ; x3 = 19,98
        assertThat(quote.unitPrice()).isEqualByComparingTo("6.66");
        assertThat(quote.totalAmount()).isEqualByComparingTo("19.98");
    }

    @Test
    void precioNegativo_esUnDatoIncoherenteDeCartelera() {
        CatalogSession broken = TestData.session("session-1", "A_LA_VENTA", TestData.FRIDAY_SHOW, new BigDecimal("-1"));

        assertThatThrownBy(() -> pricingService.quote(broken, TestData.room(), null, at(TestData.FRIDAY_SHOW), 1))
                .isInstanceOf(InvalidStateException.class);
    }
}
