package com.cinasee.componente2_reservas.services;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que se aceptan los dos formatos de fecha que puede devolver Cartelera.
 */
class CatalogTimeTest {

    @Test
    void fechaConZona_comoEnElOpenApi() {
        assertThat(CatalogTime.toInstant("2026-10-15T18:30:00Z", ZoneOffset.UTC))
                .isEqualTo(Instant.parse("2026-10-15T18:30:00Z"));
    }

    @Test
    void fechaSinZona_comoLaSerializaLocalDateTime_usaLaZonaPorDefecto() {
        assertThat(CatalogTime.toInstant("2026-10-15T18:30:00", ZoneOffset.UTC))
                .isEqualTo(Instant.parse("2026-10-15T18:30:00Z"));
        assertThat(CatalogTime.toInstant("2026-10-15T18:30:00", ZoneId.of("Europe/Madrid")))
                .isEqualTo(Instant.parse("2026-10-15T16:30:00Z"));
    }

    @Test
    void valoresNoValidos_devuelvenNull() {
        assertThat(CatalogTime.toInstant(null, ZoneOffset.UTC)).isNull();
        assertThat(CatalogTime.toInstant("mañana", ZoneOffset.UTC)).isNull();
    }
}
