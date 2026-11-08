package com.cinasee.componente2_reservas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Comprueba que el contexto completo de Spring arranca (configuración, beans,
 * entidades JPA, clientes Feign y seguridad) usando H2 en lugar de PostgreSQL.
 * No necesita Docker ni los otros microservicios.
 */
@SpringBootTest
@ActiveProfiles("test")
class Componente2ReservasApplicationTests {

	@Test
	void contextLoads() {
	}

}
