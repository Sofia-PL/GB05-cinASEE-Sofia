package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.clients.CarteleraClient;
import com.cinasee.componente2_reservas.clients.UsuariosClient;
import com.cinasee.componente2_reservas.clients.dto.CatalogRoom;
import com.cinasee.componente2_reservas.clients.dto.CatalogSession;
import com.cinasee.componente2_reservas.clients.dto.UserProfileDto;
import com.cinasee.componente2_reservas.exceptions.ForbiddenOperationException;
import com.cinasee.componente2_reservas.exceptions.ResourceNotFoundException;
import com.cinasee.componente2_reservas.exceptions.ServiceUnavailableException;
import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Fachada sobre los clientes Feign de Cartelera (Comp. 1) y Usuarios (Comp. 3).
 *
 * Su única responsabilidad es traducir los fallos HTTP de los otros
 * microservicios a excepciones de negocio con el código que marca nuestra
 * especificación OpenAPI:
 * <ul>
 *   <li>404 del servicio remoto → 404 (la sesión o el usuario no existen)</li>
 *   <li>401/403 del servicio remoto → 403 (no se tiene permiso sobre ese recurso)</li>
 *   <li>timeout, conexión rechazada o 5xx → 503 (servicio dependiente no disponible)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteDataService {

    private final CarteleraClient carteleraClient;
    private final UsuariosClient usuariosClient;

    /** Reservas -> Cartelera -> GET /sessions/{id}. */
    public CatalogSession getSession(String sessionId) {
        return call("Cartelera", () -> carteleraClient.getSession(sessionId),
                "La sesión " + sessionId + " no existe en Cartelera");
    }

    /** Reservas -> Cartelera -> GET /rooms/{id}. */
    public CatalogRoom getRoom(String roomId) {
        return call("Cartelera", () -> carteleraClient.getRoom(roomId),
                "La sala " + roomId + " no existe en Cartelera");
    }

    /** Reservas -> Usuarios -> GET /users/{id}. */
    public UserProfileDto getUser(Long userId) {
        return call("Usuarios", () -> usuariosClient.getUserProfile(userId),
                "El usuario " + userId + " no existe");
    }

    private <T> T call(String serviceName, Supplier<T> remoteCall, String notFoundMessage) {
        try {
            T result = remoteCall.get();
            if (result == null) {
                // Un 200 sin cuerpo se trata como "no existe"
                throw new ResourceNotFoundException(notFoundMessage);
            }
            return result;
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException(notFoundMessage);
        } catch (FeignException.Unauthorized | FeignException.Forbidden e) {
            throw new ForbiddenOperationException(
                    "El servicio de " + serviceName + " ha denegado el acceso con el token actual");
        } catch (RetryableException e) {
            // Conexión rechazada, DNS o timeout: el servicio no está levantado o no responde
            log.warn("{} no disponible: {}", serviceName, e.getMessage());
            throw new ServiceUnavailableException(
                    "El servicio de " + serviceName + " no está disponible en este momento", e);
        } catch (FeignException e) {
            // 5xx u otra respuesta inesperada: no podemos validar la operación
            log.warn("Respuesta inesperada de {} (HTTP {}): {}", serviceName, e.status(), e.getMessage());
            throw new ServiceUnavailableException(
                    "El servicio de " + serviceName + " no ha podido atender la petición", e);
        }
    }
}
