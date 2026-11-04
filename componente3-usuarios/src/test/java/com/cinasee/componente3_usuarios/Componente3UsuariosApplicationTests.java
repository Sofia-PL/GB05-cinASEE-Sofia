package com.cinasee.componente3_usuarios;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Test de arranque completo. Necesita MySQL en localhost:3306 y la base de
 * datos cinasee_users_db creada, por eso no se ejecuta por defecto.
 *
 * Para lanzarlo:
 *   $env:RUN_INTEGRATION_TESTS="true"; .\mvnw.cmd test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class Componente3UsuariosApplicationTests {

	@Test
	void contextLoads() {
	}

}
