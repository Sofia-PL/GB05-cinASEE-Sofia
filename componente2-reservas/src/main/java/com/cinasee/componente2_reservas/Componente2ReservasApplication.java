package com.cinasee.componente2_reservas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Componente 2 de CinAsee: Reservas, Asientos y Pagos (puerto 8082).
 *
 * <ul>
 *   <li>{@code @EnableFeignClients}: activa los clientes HTTP declarativos hacia
 *       Cartelera (Comp. 1), Usuarios (Comp. 3) y la pasarela de pago.</li>
 *   <li>{@code @ConfigurationPropertiesScan}: registra los records de configuración
 *       tipada del paquete config (TTL, tarifas, RabbitMQ).</li>
 * </ul>
 */
@SpringBootApplication
@EnableFeignClients
@ConfigurationPropertiesScan
public class Componente2ReservasApplication {

	public static void main(String[] args) {
		SpringApplication.run(Componente2ReservasApplication.class, args);
	}

}
