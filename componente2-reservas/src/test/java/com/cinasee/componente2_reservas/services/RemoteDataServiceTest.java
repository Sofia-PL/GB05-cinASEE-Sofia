package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.TestData;
import com.cinasee.componente2_reservas.clients.CarteleraClient;
import com.cinasee.componente2_reservas.clients.UsuariosClient;
import com.cinasee.componente2_reservas.exceptions.ForbiddenOperationException;
import com.cinasee.componente2_reservas.exceptions.ResourceNotFoundException;
import com.cinasee.componente2_reservas.exceptions.ServiceUnavailableException;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Comprueba la traducción de errores HTTP de Cartelera/Usuarios a los códigos de nuestra API.
 */
@ExtendWith(MockitoExtension.class)
class RemoteDataServiceTest {

    @Mock
    private CarteleraClient carteleraClient;
    @Mock
    private UsuariosClient usuariosClient;
    @InjectMocks
    private RemoteDataService service;

    private static final Request REQUEST = Request.create(Request.HttpMethod.GET, "http://cartelera/sessions/1",
            Map.of(), null, StandardCharsets.UTF_8, null);

    /** Construye la misma excepción que lanzaría Feign ante una respuesta con ese código. */
    private static FeignException httpError(int status) {
        Response response = Response.builder().status(status).reason("error").request(REQUEST).headers(Map.of()).build();
        return FeignException.errorStatus("CarteleraClient#getSession", response);
    }

    @Test
    void respuestaCorrecta_seDevuelveTalCual() {
        when(carteleraClient.getSession("session-1")).thenReturn(TestData.sessionOnSale());
        assertThat(service.getSession("session-1").status()).isEqualTo("A_LA_VENTA");
    }

    @Test
    void http404_seTraduceA404() {
        when(carteleraClient.getSession("session-1")).thenThrow(httpError(404));
        assertThatThrownBy(() -> service.getSession("session-1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void http403DeUsuarios_seTraduceA403() {
        when(usuariosClient.getUserProfile(5L)).thenThrow(httpError(403));
        assertThatThrownBy(() -> service.getUser(5L)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void http500_seTraduceA503() {
        when(carteleraClient.getRoom("room-1")).thenThrow(httpError(500));
        assertThatThrownBy(() -> service.getRoom("room-1")).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void conexionRechazada_seTraduceA503() {
        when(carteleraClient.getSession("session-1")).thenThrow(new RetryableException(-1, "Connection refused",
                Request.HttpMethod.GET, (Long) null, REQUEST));
        assertThatThrownBy(() -> service.getSession("session-1"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Cartelera");
    }

    @Test
    void cuerpoVacio_seTrataComoNoEncontrado() {
        when(usuariosClient.getUserProfile(5L)).thenReturn(null);
        assertThatThrownBy(() -> service.getUser(5L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
