package com.cinasee.componente3_usuarios.services;

import com.cinasee.componente3_usuarios.dto.PurchaseHistoryItem;
import com.cinasee.componente3_usuarios.dto.UpdateUserProfileRequest;
import com.cinasee.componente3_usuarios.dto.UserProfile;
import com.cinasee.componente3_usuarios.models.PurchaseHistory;
import com.cinasee.componente3_usuarios.models.Role;
import com.cinasee.componente3_usuarios.models.User;
import com.cinasee.componente3_usuarios.repositories.PurchaseHistoryRepository;
import com.cinasee.componente3_usuarios.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PurchaseHistoryRepository purchaseHistoryRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void getUserProfileMapsUserFields() {
        User user = user(
                42L,
                "cliente@cinasee.com",
                "María",
                Role.CLIENTE_REGISTRADO,
                1250,
                List.of("CIENCIA_FICCION")
        );
        user.setPhone("+34 600 000 000");
        user.setHomeCinema("CineAsee Centro");
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        UserProfile profile = userService.getUserProfile(42L);

        assertEquals(42L, profile.getId());
        assertEquals("cliente@cinasee.com", profile.getEmail());
        assertEquals("María", profile.getFullName());
        assertEquals("CLIENTE_REGISTRADO", profile.getRole());
        assertEquals(1250, profile.getLoyaltyPoints());
        assertEquals(List.of("CIENCIA_FICCION"), profile.getPreferredGenres());
        assertEquals("+34 600 000 000", profile.getPhone());
        assertEquals("CineAsee Centro", profile.getHomeCinema());
        assertEquals(null, profile.getCinemaId());
        assertFalse(profile.getCanReviewRoleRequests());
    }

    @Test
    void cinemaManagerCanReviewRoleRequests() {
        User user = user(
                7L,
                "staff@cinasee.com",
                "Taquillero",
                Role.STAFF_TAQUILLA,
                0,
                List.of()
        );
        user.setCinemaId("cinema-1");
        user.setCinemaManager(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        UserProfile profile = userService.getUserProfile(7L);

        assertEquals("cinema-1", profile.getCinemaId());
        assertTrue(profile.getCanReviewRoleRequests());
    }

    @Test
    void adminCanReviewRoleRequests() {
        User user = user(
                1L,
                "admin@cinasee.com",
                "Administrador",
                Role.ADMIN,
                0,
                List.of()
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserProfile profile = userService.getUserProfile(1L);

        assertTrue(profile.getCanReviewRoleRequests());
    }

    @Test
    void getUserProfileThrowsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.getUserProfile(99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void updateUserProfileUpdatesProvidedFields() {
        User user = user(42L, "cliente@cinasee.com", "María", Role.CLIENTE_REGISTRADO, 0, List.of());
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setFullName("  María Gómez  ");
        request.setPreferredGenres(List.of(" ACCION ", "ACCION", "COMEDIA", " "));
        request.setPhone("  +34 600 000 000  ");
        request.setHomeCinema("  CineAsee Centro  ");

        userService.updateUserProfile(42L, request);

        assertEquals("María Gómez", user.getFullName());
        assertEquals(List.of("ACCION", "COMEDIA"), user.getPreferredGenres());
        assertEquals("+34 600 000 000", user.getPhone());
        assertEquals("CineAsee Centro", user.getHomeCinema());
        verify(userRepository).save(user);
    }

    @Test
    void updateUserProfileCanClearPhoneAndHomeCinema() {
        User user = user(42L, "cliente@cinasee.com", "María", Role.CLIENTE_REGISTRADO, 0, List.of());
        user.setPhone("+34 600 000 000");
        user.setHomeCinema("CineAsee Centro");
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setPhone("   ");
        request.setHomeCinema("");

        userService.updateUserProfile(42L, request);

        assertEquals(null, user.getPhone());
        assertEquals(null, user.getHomeCinema());
    }

    @Test
    void getUserHistoryMapsEntitiesToDto() {
        when(userRepository.existsById(42L)).thenReturn(true);

        PurchaseHistory history = PurchaseHistory.builder()
                .reservationId(8901L)
                .userId(42L)
                .movieId("movie-1024")
                .purchaseDate(OffsetDateTime.parse("2026-09-20T18:30:00Z"))
                .amount(new BigDecimal("18.50"))
                .build();
        when(purchaseHistoryRepository.findByUserIdOrderByPurchaseDateDesc(42L))
                .thenReturn(List.of(history));

        List<PurchaseHistoryItem> result = userService.getUserHistory(42L);

        assertEquals(1, result.size());
        assertEquals(8901L, result.get(0).getReservationId());
        assertEquals("movie-1024", result.get(0).getMovieId());
        assertEquals(18.50f, result.get(0).getAmount(), 0.001f);
        assertEquals(OffsetDateTime.parse("2026-09-20T18:30:00Z"), result.get(0).getPurchaseDate());
    }

    @Test
    void getUserHistoryThrowsNotFoundWhenUserDoesNotExist() {
        when(userRepository.existsById(99L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.getUserHistory(99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(purchaseHistoryRepository);
    }

    private User user(Long id, String email, String fullName, Role role, int points, List<String> genres) {
        return User.builder()
                .id(id)
                .email(email)
                .passwordHash("hashed")
                .fullName(fullName)
                .role(role)
                .loyaltyPoints(points)
                .preferredGenres(new ArrayList<>(genres))
                .build();
    }
}
