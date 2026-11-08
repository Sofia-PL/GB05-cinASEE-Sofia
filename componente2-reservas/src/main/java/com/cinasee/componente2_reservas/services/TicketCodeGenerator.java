package com.cinasee.componente2_reservas.services;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Genera el identificador único de cada entrada, que es lo que se codifica en el
 * QR / PKPass. Formato (como en el ejemplo del OpenAPI):
 *
 * <pre>QR-{reservationId}-{fila}-{columna}-{sufijo aleatorio}</pre>
 *
 * El sufijo aleatorio (8 caracteres de {@link SecureRandom}) impide adivinar
 * códigos válidos a partir de la reserva y la butaca. Además, la columna
 * ticket_code tiene una restricción UNIQUE en la base de datos.
 */
@Component
public class TicketCodeGenerator {

    /** Alfabeto sin caracteres ambiguos (0/O, 1/I) para facilitar la lectura manual. */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int SUFFIX_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String generate(Long reservationId, int row, int column) {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return "QR-" + reservationId + "-" + row + "-" + column + "-" + suffix;
    }
}
