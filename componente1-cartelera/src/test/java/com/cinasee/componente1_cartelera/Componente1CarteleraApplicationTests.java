package com.cinasee.componente1_cartelera;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.MongoDatabaseFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
    "SPRING_MONGODB_URI=mongodb://mongo-test:27018/component1_test",
    "app.seed.enabled=false",
    "app.scheduling.enabled=false"
})
class Componente1CarteleraApplicationTests {

    @Autowired
    private MongoDatabaseFactory mongoDatabaseFactory;

    @Test
    void contextLoads() {
    }

    @Test
    void configuresMongoDatabaseFromEnvironmentUri() {
        assertEquals("component1_test", mongoDatabaseFactory.getMongoDatabase().getName());
    }

}
