package com.cinasee.componente3_usuarios.controllers;

import com.cinasee.componente3_usuarios.config.SecurityConfig;
import com.cinasee.componente3_usuarios.dto.RoleRequest;
import com.cinasee.componente3_usuarios.dto.RoleRequestCreateRequest;
import com.cinasee.componente3_usuarios.dto.RoleRequestReviewRequest;
import com.cinasee.componente3_usuarios.services.RoleRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoleRequestController.class)
@Import({SecurityConfig.class, RoleRequestControllerTest.AopConfig.class})
class RoleRequestControllerTest {

    @TestConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class AopConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleRequestService roleRequestService;

    @Test
    void clientCanCreateRoleRequest() throws Exception {
        when(roleRequestService.createRoleRequest(eq(3L), any(RoleRequestCreateRequest.class)))
                .thenReturn(pendingRequest());

        mockMvc.perform(post("/role-requests")
                        .with(userJwt(3L, "CLIENTE_REGISTRADO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedRole": "STAFF_TAQUILLA",
                                  "cinemaId": "cinema-1",
                                  "motivation": "Trabajo en taquilla"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void adminCannotCreateRoleRequest() throws Exception {
        mockMvc.perform(post("/role-requests")
                        .with(userJwt(1L, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedRole\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        verify(roleRequestService, never()).createRoleRequest(any(), any());
    }

    @Test
    void guestCannotCreateRoleRequest() throws Exception {
        mockMvc.perform(post("/role-requests")
                        .with(jwt()
                                .jwt(token -> token.subject("guest:abc").claim("role", "VISITANTE"))
                                .authorities(new SimpleGrantedAuthority("ROLE_VISITANTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedRole\":\"STAFF_TAQUILLA\",\"cinemaId\":\"cinema-1\"}"))
                .andExpect(status().isForbidden());

        verify(roleRequestService, never()).createRoleRequest(any(), any());
    }

    @Test
    void cinemaManagerCanListRequestsOfOwnCinema() throws Exception {
        when(roleRequestService.listRoleRequests(7L, "PENDING", "cinema-1"))
                .thenReturn(List.of(pendingRequest()));

        mockMvc.perform(get("/role-requests")
                        .param("status", "PENDING")
                        .param("cinemaId", "cinema-1")
                        .with(userJwt(7L, "STAFF_TAQUILLA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void clientCannotListRoleRequests() throws Exception {
        mockMvc.perform(get("/role-requests").with(userJwt(3L, "CLIENTE_REGISTRADO")))
                .andExpect(status().isForbidden());

        verify(roleRequestService, never()).listRoleRequests(any(), any(), any());
    }

    @Test
    void adminCanReviewRoleRequest() throws Exception {
        when(roleRequestService.reviewRoleRequest(eq(1L), eq(10L), any(RoleRequestReviewRequest.class)))
                .thenReturn(pendingRequest());

        mockMvc.perform(patch("/role-requests/10")
                        .with(userJwt(1L, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"reviewComment\":\"Aprobado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void userCanReadOwnRoleRequests() throws Exception {
        when(roleRequestService.getMyRoleRequests(3L)).thenReturn(List.of(pendingRequest()));

        mockMvc.perform(get("/role-requests/me").with(userJwt(3L, "CLIENTE_REGISTRADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(3));
    }

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/role-requests/me"))
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

    private static RoleRequest pendingRequest() {
        RoleRequest dto = new RoleRequest(
                10L,
                3L,
                RoleRequest.RequestedRoleEnum.STAFF_TAQUILLA,
                RoleRequest.StatusEnum.PENDING,
                OffsetDateTime.parse("2026-11-09T10:00:00Z")
        );
        dto.setCinemaId("cinema-1");
        dto.setMotivation("Trabajo en taquilla");
        return dto;
    }
}
