package com.cinasee.componente2_reservas.services;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios del generador de códigos de entrada.
 */
class TicketCodeGeneratorTest {

    private final TicketCodeGenerator generator = new TicketCodeGenerator();

    @Test
    void codeFollowsOpenApiFormat() {
        String code = generator.generate(8901L, 5, 12);

        assertThat(code).matches("QR-8901-5-12-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}");
    }

    @Test
    void suffixAvoidsAmbiguousCharacters() {
        for (int i = 0; i < 200; i++) {
            String suffix = generator.generate(1L, 1, 1).substring("QR-1-1-1-".length());
            assertThat(suffix).doesNotContain("0", "O", "1", "I");
        }
    }

    @Test
    void codesForSameSeatAreDifferent() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            codes.add(generator.generate(1L, 1, 1));
        }
        assertThat(codes).hasSize(500);
    }
}
