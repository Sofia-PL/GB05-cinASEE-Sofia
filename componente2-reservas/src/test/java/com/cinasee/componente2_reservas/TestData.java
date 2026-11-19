package com.cinasee.componente2_reservas;

import com.cinasee.componente2_reservas.clients.dto.CatalogRoom;
import com.cinasee.componente2_reservas.clients.dto.CatalogSession;
import com.cinasee.componente2_reservas.clients.dto.UserProfileDto;
import com.cinasee.componente2_reservas.config.PricingProperties;
import com.cinasee.componente2_reservas.config.RedsysProperties;
import com.cinasee.componente2_reservas.config.ReservationProperties;
import com.cinasee.componente2_reservas.dto.CreateReservationRequest;
import com.cinasee.componente2_reservas.dto.SeatSelection;
import com.cinasee.componente2_reservas.security.CallerIdentity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

/**
 * Datos de prueba compartidos por los tests.
 *
 * "Ahora" es siempre el jueves 2026-10-08 12:00 UTC, así los tests no dependen
 * de la fecha real en que se ejecutan.
 */
public final class TestData {

    public static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    public static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    public static final String SHOW_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";
    public static final String MOVIE_ID = "movie-101";
    public static final String ROOM_ID = "room-1";
    public static final long USER_ID = 5590L;

    /** Viernes 16/10/2026: sin descuento de día del espectador. */
    public static final String FRIDAY_SHOW = "2026-10-16T18:30:00";
    /** Miércoles 14/10/2026: día del espectador. */
    public static final String WEDNESDAY_SHOW = "2026-10-14T18:30:00";

    public static final CallerIdentity CLIENT = new CallerIdentity(String.valueOf(USER_ID), CallerIdentity.ROLE_CLIENT);
    public static final CallerIdentity OTHER_CLIENT = new CallerIdentity("7777", CallerIdentity.ROLE_CLIENT);
    public static final CallerIdentity GUEST = new CallerIdentity("guest:3f2c7a1e", CallerIdentity.ROLE_GUEST);
    public static final CallerIdentity STAFF = new CallerIdentity("1", CallerIdentity.ROLE_STAFF);

    private TestData() {
    }

    public static ReservationProperties reservationProperties() {
        return new ReservationProperties(Duration.ofMinutes(10), "EUR", 100, ZoneOffset.UTC, Duration.ofMinutes(15));
    }

    /**
     * Clave INVENTADA para los tests de Redsys (24 bytes aleatorios en Base64). No es la
     * clave genérica de Redsys: los tests no dependen de ninguna credencial externa.
     */
    public static final String TEST_REDSYS_KEY = "Mk9m98IfEblmPfrpsawt7BmxObt98Jev";

    public static RedsysProperties redsysProperties() {
        return new RedsysProperties("https://sis-t.redsys.es:25443/sis/realizarPago", "999008881", "1",
                TEST_REDSYS_KEY, "978", "http://localhost:8082/ok", "http://localhost:8082/ko", "");
    }

    public static PricingProperties pricingProperties() {
        return new PricingProperties(DayOfWeek.WEDNESDAY, new BigDecimal("0.20"), 500,
                new BigDecimal("0.10"), ZoneId.of("Europe/Madrid"));
    }

    public static CatalogSession sessionOnSale() {
        return session(SHOW_ID, "A_LA_VENTA", FRIDAY_SHOW, new BigDecimal("8.50"));
    }

    public static CatalogSession session(String id, String status, String startTime, BigDecimal price) {
        return new CatalogSession(id, MOVIE_ID, ROOM_ID, startTime, null, status, price);
    }

    /** Sala de 10 filas x 15 columnas, precio base 7,50 €. */
    public static CatalogRoom room() {
        return new CatalogRoom(ROOM_ID, "Sala 1", "ESTANDAR_2D", 150, 10, 15, true, new BigDecimal("7.50"));
    }

    public static UserProfileDto user(int loyaltyPoints) {
        return new UserProfileDto(USER_ID, "cliente@cinasee.com", "María Gómez", "CLIENTE_REGISTRADO",
                loyaltyPoints, List.of("DRAMA"));
    }

    /** Crea butacas a partir de pares fila, columna: seats(8, 12, 8, 13). */
    public static List<SeatSelection> seats(int... rowColumnPairs) {
        if (rowColumnPairs.length % 2 != 0) {
            throw new IllegalArgumentException("Se esperan pares fila/columna: " + Arrays.toString(rowColumnPairs));
        }
        SeatSelection[] result = new SeatSelection[rowColumnPairs.length / 2];
        for (int i = 0; i < rowColumnPairs.length; i += 2) {
            result[i / 2] = new SeatSelection(rowColumnPairs[i], rowColumnPairs[i + 1]);
        }
        return List.of(result);
    }

    public static CreateReservationRequest request(Long userId, String guestEmail, List<SeatSelection> seats) {
        return new CreateReservationRequest(SHOW_ID, userId, guestEmail, seats);
    }
}
