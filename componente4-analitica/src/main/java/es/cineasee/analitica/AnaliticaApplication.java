package es.cineasee.analitica;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class AnaliticaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnaliticaApplication.class, args);
    }
}

