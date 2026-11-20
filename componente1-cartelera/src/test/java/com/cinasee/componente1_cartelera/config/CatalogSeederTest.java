package com.cinasee.componente1_cartelera.config;

import com.cinasee.componente1_cartelera.models.Session;
import com.cinasee.componente1_cartelera.repositories.CinemaRepository;
import com.cinasee.componente1_cartelera.repositories.MovieRepository;
import com.cinasee.componente1_cartelera.repositories.RoomRepository;
import com.cinasee.componente1_cartelera.repositories.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogSeederTest {

    @Mock
    private CinemaRepository cinemaRepository;
    @Mock
    private MovieRepository movieRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private SessionRepository sessionRepository;

    @InjectMocks
    private CatalogSeeder seeder;

    @Test
    void creaSesionesDeEjemploParaDosSemanasSinIdsRepetidos() {
        when(sessionRepository.findByRoomIdAndStatusNot(anyString(), eq("CANCELADA"))).thenReturn(List.of());

        seeder.run();

        ArgumentCaptor<Session> guardadas = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository, atLeastOnce()).save(guardadas.capture());
        List<Session> sesiones = guardadas.getAllValues();

        // 48 de la programación base + días 4 a 14 (6 días como el día 2 y 5 como el día 1)
        assertEquals(48 + 6 * 20 + 5 * 22, sesiones.size());
        Set<String> ids = sesiones.stream().map(Session::getId).collect(Collectors.toSet());
        assertEquals(sesiones.size(), ids.size(), "Los ids de las sesiones no se pueden repetir");

        LocalDate hoy = LocalDate.now(ZoneId.of("Europe/Madrid"));
        Set<Long> dias = sesiones.stream()
                .map(s -> ChronoUnit.DAYS.between(hoy, s.getStartTime().toLocalDate()))
                .collect(Collectors.toSet());
        assertEquals(14, dias.size());
        assertTrue(dias.stream().allMatch(d -> d >= 1 && d <= 14), "Todas las sesiones son de los próximos 14 días");
    }

    @Test
    void elIdDeLaCopiaEsUnUlidValidoYDistintoPorDia() {
        String original = "01M7SR8QHR000000000000001G";

        String copiaDia7 = CatalogSeeder.idDeCopia(original, 7);

        assertEquals("01M7SR8QHS070000000000001G", copiaDia7);
        assertEquals(26, copiaDia7.length());
        assertTrue(copiaDia7.matches("[0-9A-HJKMNP-TV-Z]{26}"), "Solo caracteres válidos de ULID (Crockford)");
        assertTrue(!copiaDia7.equals(CatalogSeeder.idDeCopia(original, 8)));
    }
}
