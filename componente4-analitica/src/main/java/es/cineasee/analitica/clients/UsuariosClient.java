package es.cineasee.analitica.clients;

import es.cineasee.analitica.clients.dto.PurchaseHistoryItemDTO;
import es.cineasee.analitica.clients.dto.UserProfileDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(
        name = "usuarios-service",
        url = "${services.usuarios.url:http://localhost:8083/v1/iam}",
        configuration = ForwardJwtFeignConfig.class
)
public interface UsuariosClient {

    @GetMapping("/users/{userId}")
    UserProfileDTO getUserProfile(@PathVariable("userId") Long userId);

    @GetMapping("/users/{userId}/history")
    List<PurchaseHistoryItemDTO> getUserHistory(@PathVariable("userId") Long userId);
}

