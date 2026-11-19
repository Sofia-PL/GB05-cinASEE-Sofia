package es.cineasee.analitica;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Comprueba que el contexto de Spring arranca con el perfil de test
 * (H2 en memoria, sin RabbitMQ ni PostgreSQL reales).
 */
@SpringBootTest(classes = AnaliticaApplication.class)
@ActiveProfiles("test")
class AnaliticaApplicationTests {

    @Test
    void contextLoads() {
    }

}
