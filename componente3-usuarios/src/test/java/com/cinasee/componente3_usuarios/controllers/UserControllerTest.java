package com.cinasee.componente3_usuarios.controllers;

import com.cinasee.componente3_usuarios.config.SecurityConfig;
import com.cinasee.componente3_usuarios.dto.PurchaseHistoryItem;
import com.cinasee.componente3_usuarios.dto.UpdateUserProfileRequest;
import com.cinasee.componente3_usuarios.dto.UserProfile;
import com.cinasee.componente3_usuarios.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, UserControllerTest.AopConfig.class})
class UserControllerTest {

    /**
     * En la aplicación real Spring Boot activa proxies CGLIB para AOP. El slice
     * @WebMvcTest no carga AopAutoConfiguration, así que lo forzamos aquí; sin
     * esto el controlador se proxya con JDK y deja de detectarse como handler.
     */
    @TestConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class AopConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void ownerCanReadOwnProfile() throws Exception {
        when(userService.getUserProfile(42L)).thenReturn(profile(42L));

        mockMvc.perform(get("/users/42").with(userJwt(42L, "CLIENTE_REGISTRADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.email").value("cliente42@cinasee.com"))
                .andExpect(jsonPath("$.phone").value("+34 600 000 000"))
                .andExpect(jsonPath("$.homeCinema").value("CineAsee Centro"))
                .andExpect(jsonPath("$.canReviewRoleRequests").value(false));
    }

    @Test
    void clientCannotReadAnotherUsersProfile() throws Exception {
        mockMvc.perform(get("/users/42").with(userJwt(99L, "CLIENTE_REGISTRADO")))
                .andExpect(status().isForbidden());

        verify(userService, never()).getUserProfile(any());
    }

    @Test
    void adminCanReadAnyProfile() throws Exception {
        when(userService.getUserProfile(42L)).thenReturn(profile(42L));

        mockMvc.perform(get("/users/42").with(userJwt(1L, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void staffCanReadAnyHistory() throws Exception {
        when(userService.getUserHistory(42L)).thenReturn(List.of(historyItem()));

        mockMvc.perform(get("/users/42/history").with(userJwt(7L, "STAFF_TAQUILLA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reservationId").value(8901));
    }

    @Test
    void clientCannotReadAnotherUsersHistory() throws Exception {
        mockMvc.perform(get("/users/42/history").with(userJwt(99L, "CLIENTE_REGISTRADO")))
                .andExpect(status().isForbidden());

        verify(userService, never()).getUserHistory(any());
    }

    @Test
    void ownerCanUpdateOwnProfile() throws Exception {
        mockMvc.perform(put("/users/42")
                        .with(userJwt(42L, "CLIENTE_REGISTRADO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "María Gómez",
                                  "preferredGenres": ["ACCION"],
                                  "phone": "+34 600 000 000",
                                  "homeCinema": "CineAsee Centro"
                                }
                                """))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<UpdateUserProfileRequest> captor =
                org.mockito.ArgumentCaptor.forClass(UpdateUserProfileRequest.class);
        verify(userService).updateUserProfile(org.mockito.ArgumentMatchers.eq(42L), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("+34 600 000 000", captor.getValue().getPhone());
        org.junit.jupiter.api.Assertions.assertEquals("CineAsee Centro", captor.getValue().getHomeCinema());
    }

    @Test
    void clientCannotUpdateAnotherUsersProfile() throws Exception {
        mockMvc.perform(put("/users/42")
                        .with(userJwt(99L, "CLIENTE_REGISTRADO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Intruso\"}"))
                .andExpect(status().isForbidden());

        verify(userService, never()).updateUserProfile(any(), any());
    }

    @Test
    void guestTokenCannotAccessUserData() throws Exception {
        mockMvc.perform(get("/users/42").with(jwt()
                        .jwt(token -> token.subject("guest:abc").claim("role", "VISITANTE"))
                        .authorities(new SimpleGrantedAuthority("ROLE_VISITANTE"))))
                .andExpect(status().isForbidden());

        verify(userService, never()).getUserProfile(any());
    }

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/users/42"))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor userJwt(
            Long userId,
            String role
    ) {
        return jwt()
                .jwt(token -> token.subject(String.valueOf(userId)).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static UserProfile profile(Long id) {
        UserProfile profile = new UserProfile(
                id,
                "cliente" + id + "@cinasee.com",
                "Cliente " + id,
                "CLIENTE_REGISTRADO",
                100
        );
        profile.setPreferredGenres(List.of("ACCION"));
        profile.setPhone("+34 600 000 000");
        profile.setHomeCinema("CineAsee Centro");
        profile.setCanReviewRoleRequests(false);
        return profile;
    }

    private static PurchaseHistoryItem historyItem() {
        return new PurchaseHistoryItem(
                8901L,
                "movie-1024",
                OffsetDateTime.parse("2026-09-20T18:30:00Z"),
                18.5f
        );
    }
}
