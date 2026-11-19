package com.cinasee.componente3_usuarios.services;

import com.cinasee.componente3_usuarios.dto.RoleRequestCreateRequest;
import com.cinasee.componente3_usuarios.dto.RoleRequestReviewRequest;
import com.cinasee.componente3_usuarios.models.Role;
import com.cinasee.componente3_usuarios.models.RoleRequest;
import com.cinasee.componente3_usuarios.models.RoleRequestStatus;
import com.cinasee.componente3_usuarios.models.User;
import com.cinasee.componente3_usuarios.repositories.RoleRequestRepository;
import com.cinasee.componente3_usuarios.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleRequestServiceImplTest {

    @Mock
    private RoleRequestRepository roleRequestRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RoleRequestServiceImpl roleRequestService;

    @Test
    void clientCanCreateStaffRequest() {
        User requester = user(3L, Role.CLIENTE_REGISTRADO, null, false);
        when(userRepository.findById(3L)).thenReturn(Optional.of(requester));
        when(roleRequestRepository.existsByUserIdAndStatus(3L, RoleRequestStatus.PENDING)).thenReturn(false);
        when(roleRequestRepository.save(any(RoleRequest.class))).thenAnswer(invocation -> {
            RoleRequest saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        RoleRequestCreateRequest request = new RoleRequestCreateRequest(
                RoleRequestCreateRequest.RequestedRoleEnum.STAFF_TAQUILLA
        );
        request.setCinemaId("cinema-1");
        request.setMotivation("  Trabajo en taquilla  ");

        com.cinasee.componente3_usuarios.dto.RoleRequest result =
                roleRequestService.createRoleRequest(3L, request);

        assertEquals(10L, result.getId());
        assertEquals(3L, result.getUserId());
        assertEquals(com.cinasee.componente3_usuarios.dto.RoleRequest.StatusEnum.PENDING, result.getStatus());
        assertEquals("cinema-1", result.getCinemaId());
        assertEquals("Trabajo en taquilla", result.getMotivation());
    }

    @Test
    void adminCannotRequestAnotherRole() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.ADMIN, null, false)));

        RoleRequestCreateRequest request = new RoleRequestCreateRequest(
                RoleRequestCreateRequest.RequestedRoleEnum.ADMIN
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> roleRequestService.createRoleRequest(1L, request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(roleRequestRepository, never()).save(any());
    }

    @Test
    void staffRequestRequiresCinema() {
        when(userRepository.findById(3L)).thenReturn(
                Optional.of(user(3L, Role.CLIENTE_REGISTRADO, null, false))
        );

        RoleRequestCreateRequest request = new RoleRequestCreateRequest(
                RoleRequestCreateRequest.RequestedRoleEnum.STAFF_TAQUILLA
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> roleRequestService.createRoleRequest(3L, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void userCannotHaveTwoPendingRequests() {
        when(userRepository.findById(3L)).thenReturn(
                Optional.of(user(3L, Role.CLIENTE_REGISTRADO, null, false))
        );
        when(roleRequestRepository.existsByUserIdAndStatus(3L, RoleRequestStatus.PENDING)).thenReturn(true);

        RoleRequestCreateRequest request = new RoleRequestCreateRequest(
                RoleRequestCreateRequest.RequestedRoleEnum.STAFF_TAQUILLA
        );
        request.setCinemaId("cinema-1");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> roleRequestService.createRoleRequest(3L, request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void adminListsAllRequests() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.ADMIN, null, false)));
        when(roleRequestRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(roleRequest(10L, 3L, Role.STAFF_TAQUILLA, "cinema-1", RoleRequestStatus.PENDING)));

        List<com.cinasee.componente3_usuarios.dto.RoleRequest> result =
                roleRequestService.listRoleRequests(1L, null, null);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
    }

    @Test
    void cinemaManagerOnlyListsStaffRequestsOfOwnCinema() {
        when(userRepository.findById(7L)).thenReturn(
                Optional.of(user(7L, Role.STAFF_TAQUILLA, "cinema-1", true))
        );
        when(roleRequestRepository.findByCinemaIdAndRequestedRoleAndStatusOrderByCreatedAtDesc(
                "cinema-1",
                Role.STAFF_TAQUILLA,
                RoleRequestStatus.PENDING
        )).thenReturn(List.of(roleRequest(10L, 3L, Role.STAFF_TAQUILLA, "cinema-1", RoleRequestStatus.PENDING)));

        List<com.cinasee.componente3_usuarios.dto.RoleRequest> result =
                roleRequestService.listRoleRequests(7L, "PENDING", "cinema-1");

        assertEquals(1, result.size());
        assertEquals("cinema-1", result.get(0).getCinemaId());
    }

    @Test
    void cinemaManagerCannotListAnotherCinema() {
        when(userRepository.findById(7L)).thenReturn(
                Optional.of(user(7L, Role.STAFF_TAQUILLA, "cinema-1", true))
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> roleRequestService.listRoleRequests(7L, "PENDING", "cinema-2")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void adminCanApproveAndUpdatesRequesterRoleAndCinema() {
        User reviewer = user(1L, Role.ADMIN, null, false);
        User requester = user(3L, Role.CLIENTE_REGISTRADO, null, false);
        RoleRequest pending = roleRequest(10L, 3L, Role.STAFF_TAQUILLA, "cinema-1", RoleRequestStatus.PENDING);

        when(userRepository.findById(1L)).thenReturn(Optional.of(reviewer));
        when(roleRequestRepository.findById(10L)).thenReturn(Optional.of(pending));
        when(userRepository.findById(3L)).thenReturn(Optional.of(requester));
        when(roleRequestRepository.save(pending)).thenReturn(pending);

        RoleRequestReviewRequest review = new RoleRequestReviewRequest(
                RoleRequestReviewRequest.StatusEnum.APPROVED
        );
        review.setReviewComment("  Aprobado  ");

        com.cinasee.componente3_usuarios.dto.RoleRequest result =
                roleRequestService.reviewRoleRequest(1L, 10L, review);

        assertEquals(com.cinasee.componente3_usuarios.dto.RoleRequest.StatusEnum.APPROVED, result.getStatus());
        assertEquals(1L, result.getReviewedBy());
        assertEquals("Aprobado", result.getReviewComment());
        assertEquals(Role.STAFF_TAQUILLA, requester.getRole());
        assertEquals("cinema-1", requester.getCinemaId());
        verify(userRepository).save(requester);
    }

    @Test
    void rejectionDoesNotChangeRequesterRole() {
        User reviewer = user(1L, Role.ADMIN, null, false);
        RoleRequest pending = roleRequest(10L, 3L, Role.STAFF_TAQUILLA, "cinema-1", RoleRequestStatus.PENDING);

        when(userRepository.findById(1L)).thenReturn(Optional.of(reviewer));
        when(roleRequestRepository.findById(10L)).thenReturn(Optional.of(pending));
        when(roleRequestRepository.save(pending)).thenReturn(pending);

        RoleRequestReviewRequest review = new RoleRequestReviewRequest(
                RoleRequestReviewRequest.StatusEnum.REJECTED
        );

        com.cinasee.componente3_usuarios.dto.RoleRequest result =
                roleRequestService.reviewRoleRequest(1L, 10L, review);

        assertEquals(com.cinasee.componente3_usuarios.dto.RoleRequest.StatusEnum.REJECTED, result.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void cinemaManagerCannotReviewRequestFromAnotherCinema() {
        User reviewer = user(7L, Role.STAFF_TAQUILLA, "cinema-1", true);
        RoleRequest pending = roleRequest(10L, 3L, Role.STAFF_TAQUILLA, "cinema-2", RoleRequestStatus.PENDING);

        when(userRepository.findById(7L)).thenReturn(Optional.of(reviewer));
        when(roleRequestRepository.findById(10L)).thenReturn(Optional.of(pending));

        RoleRequestReviewRequest review = new RoleRequestReviewRequest(
                RoleRequestReviewRequest.StatusEnum.APPROVED
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> roleRequestService.reviewRoleRequest(7L, 10L, review)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void cinemaManagerCannotReviewAdminRequest() {
        User reviewer = user(7L, Role.STAFF_TAQUILLA, "cinema-1", true);
        RoleRequest pending = roleRequest(10L, 3L, Role.ADMIN, "cinema-1", RoleRequestStatus.PENDING);

        when(userRepository.findById(7L)).thenReturn(Optional.of(reviewer));
        when(roleRequestRepository.findById(10L)).thenReturn(Optional.of(pending));

        RoleRequestReviewRequest review = new RoleRequestReviewRequest(
                RoleRequestReviewRequest.StatusEnum.APPROVED
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> roleRequestService.reviewRoleRequest(7L, 10L, review)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void userCannotReviewOwnRequest() {
        User reviewer = user(3L, Role.ADMIN, null, false);
        RoleRequest pending = roleRequest(10L, 3L, Role.STAFF_TAQUILLA, "cinema-1", RoleRequestStatus.PENDING);

        when(userRepository.findById(3L)).thenReturn(Optional.of(reviewer));
        when(roleRequestRepository.findById(10L)).thenReturn(Optional.of(pending));

        RoleRequestReviewRequest review = new RoleRequestReviewRequest(
                RoleRequestReviewRequest.StatusEnum.APPROVED
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> roleRequestService.reviewRoleRequest(3L, 10L, review)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void cannotReviewRequestThatIsNoLongerPending() {
        User reviewer = user(1L, Role.ADMIN, null, false);
        RoleRequest reviewed = roleRequest(10L, 3L, Role.STAFF_TAQUILLA, "cinema-1", RoleRequestStatus.APPROVED);

        when(userRepository.findById(1L)).thenReturn(Optional.of(reviewer));
        when(roleRequestRepository.findById(10L)).thenReturn(Optional.of(reviewed));

        RoleRequestReviewRequest review = new RoleRequestReviewRequest(
                RoleRequestReviewRequest.StatusEnum.REJECTED
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> roleRequestService.reviewRoleRequest(1L, 10L, review)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    private User user(Long id, Role role, String cinemaId, boolean cinemaManager) {
        return User.builder()
                .id(id)
                .email("user" + id + "@cinasee.com")
                .passwordHash("hashed")
                .fullName("Usuario " + id)
                .role(role)
                .loyaltyPoints(0)
                .preferredGenres(new ArrayList<>())
                .cinemaId(cinemaId)
                .cinemaManager(cinemaManager)
                .build();
    }

    private RoleRequest roleRequest(
            Long id,
            Long userId,
            Role requestedRole,
            String cinemaId,
            RoleRequestStatus status
    ) {
        return RoleRequest.builder()
                .id(id)
                .userId(userId)
                .requestedRole(requestedRole)
                .cinemaId(cinemaId)
                .status(status)
                .createdAt(OffsetDateTime.parse("2026-11-09T10:00:00Z"))
                .build();
    }
}
