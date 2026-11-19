package com.cinasee.componente3_usuarios.services;

import com.cinasee.componente3_usuarios.dto.RoleRequestCreateRequest;
import com.cinasee.componente3_usuarios.dto.RoleRequestReviewRequest;
import com.cinasee.componente3_usuarios.models.Role;
import com.cinasee.componente3_usuarios.models.RoleRequest;
import com.cinasee.componente3_usuarios.models.RoleRequestStatus;
import com.cinasee.componente3_usuarios.models.User;
import com.cinasee.componente3_usuarios.repositories.RoleRequestRepository;
import com.cinasee.componente3_usuarios.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleRequestServiceImpl implements RoleRequestService {

    private final RoleRequestRepository roleRequestRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public com.cinasee.componente3_usuarios.dto.RoleRequest createRoleRequest(
            Long requesterId,
            RoleRequestCreateRequest request
    ) {
        User requester = findUser(requesterId);

        if (requester.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Un administrador no puede solicitar otro rol"
            );
        }

        Role requestedRole = toRole(request.getRequestedRole());
        if (requestedRole == null || requestedRole == Role.VISITANTE || requestedRole == Role.CLIENTE_REGISTRADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol solicitado no válido");
        }

        if (requestedRole == Role.STAFF_TAQUILLA && request.getCinemaId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El cine es obligatorio para solicitar STAFF_TAQUILLA"
            );
        }
        if (request.getCinemaId() != null && request.getCinemaId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cine indicado no es válido");
        }
        if (requester.getRole() == requestedRole) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El usuario ya tiene ese rol");
        }
        if (roleRequestRepository.existsByUserIdAndStatus(requesterId, RoleRequestStatus.PENDING)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una solicitud de rol pendiente para este usuario"
            );
        }

        RoleRequest roleRequest = RoleRequest.builder()
                .userId(requesterId)
                .requestedRole(requestedRole)
                .cinemaId(request.getCinemaId())
                .motivation(normalizeText(request.getMotivation()))
                .status(RoleRequestStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();

        return toDto(roleRequestRepository.save(roleRequest));
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.cinasee.componente3_usuarios.dto.RoleRequest> getMyRoleRequests(Long requesterId) {
        findUser(requesterId);
        return roleRequestRepository.findByUserIdOrderByCreatedAtDesc(requesterId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.cinasee.componente3_usuarios.dto.RoleRequest> listRoleRequests(
            Long reviewerId,
            String status,
            String cinemaId
    ) {
        User reviewer = findUser(reviewerId);
        RoleRequestStatus statusFilter = parseStatus(status);

        if (reviewer.getRole() == Role.ADMIN) {
            return listForAdmin(statusFilter, cinemaId);
        }

        if (reviewer.getRole() == Role.STAFF_TAQUILLA && reviewer.isCinemaManager()) {
            if (reviewer.getCinemaId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "El taquillero no tiene un cine asignado"
                );
            }
            if (cinemaId != null && !cinemaId.equals(reviewer.getCinemaId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Solo puede revisar solicitudes de su propio cine"
                );
            }
            return listForCinemaManager(reviewer.getCinemaId(), statusFilter);
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "El usuario no puede revisar solicitudes de rol"
        );
    }

    @Override
    @Transactional
    public com.cinasee.componente3_usuarios.dto.RoleRequest reviewRoleRequest(
            Long reviewerId,
            Long requestId,
            RoleRequestReviewRequest request
    ) {
        User reviewer = findUser(reviewerId);
        RoleRequest roleRequest = roleRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Solicitud de rol no encontrada con ID: " + requestId
                ));

        if (roleRequest.getStatus() != RoleRequestStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La solicitud ya no está pendiente"
            );
        }
        if (roleRequest.getUserId().equals(reviewerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No puede revisar su propia solicitud"
            );
        }
        validateReviewerCanReview(reviewer, roleRequest);

        if (request.getStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La decisión es obligatoria");
        }
        RoleRequestStatus decision = RoleRequestStatus.valueOf(request.getStatus().name());
        if (decision == RoleRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La decisión no es válida");
        }

        roleRequest.setStatus(decision);
        roleRequest.setReviewedAt(OffsetDateTime.now());
        roleRequest.setReviewedBy(reviewerId);
        roleRequest.setReviewComment(normalizeText(request.getReviewComment()));

        if (decision == RoleRequestStatus.APPROVED) {
            User requester = findUser(roleRequest.getUserId());
            requester.setRole(roleRequest.getRequestedRole());
            requester.setCinemaId(roleRequest.getCinemaId());
            userRepository.save(requester);
        }

        return toDto(roleRequestRepository.save(roleRequest));
    }

    private List<com.cinasee.componente3_usuarios.dto.RoleRequest> listForAdmin(
            RoleRequestStatus status,
            String cinemaId
    ) {
        List<RoleRequest> requests;
        if (cinemaId != null) {
            requests = status == null
                    ? roleRequestRepository.findByCinemaIdOrderByCreatedAtDesc(cinemaId)
                    : roleRequestRepository.findByCinemaIdAndStatusOrderByCreatedAtDesc(cinemaId, status);
        } else {
            requests = status == null
                    ? roleRequestRepository.findAllByOrderByCreatedAtDesc()
                    : roleRequestRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return requests.stream().map(this::toDto).toList();
    }

    private List<com.cinasee.componente3_usuarios.dto.RoleRequest> listForCinemaManager(
            String cinemaId,
            RoleRequestStatus status
    ) {
        List<RoleRequest> requests = status == null
                ? roleRequestRepository.findByCinemaIdAndRequestedRoleOrderByCreatedAtDesc(
                        cinemaId,
                        Role.STAFF_TAQUILLA
                )
                : roleRequestRepository.findByCinemaIdAndRequestedRoleAndStatusOrderByCreatedAtDesc(
                        cinemaId,
                        Role.STAFF_TAQUILLA,
                        status
                );
        return requests.stream().map(this::toDto).toList();
    }

    private void validateReviewerCanReview(User reviewer, RoleRequest roleRequest) {
        if (reviewer.getRole() == Role.ADMIN) {
            return;
        }

        boolean isCinemaManager = reviewer.getRole() == Role.STAFF_TAQUILLA
                && reviewer.isCinemaManager();

        if (!isCinemaManager
                || roleRequest.getRequestedRole() != Role.STAFF_TAQUILLA
                || reviewer.getCinemaId() == null
                || !reviewer.getCinemaId().equals(roleRequest.getCinemaId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "El usuario no puede revisar esta solicitud"
            );
        }
    }

    private RoleRequestStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RoleRequestStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Estado de solicitud no válido: " + status
            );
        }
    }

    private Role toRole(RoleRequestCreateRequest.RequestedRoleEnum requestedRole) {
        if (requestedRole == null) {
            return null;
        }
        return Role.valueOf(requestedRole.name());
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Usuario no encontrado con ID: " + userId
                ));
    }

    private com.cinasee.componente3_usuarios.dto.RoleRequest toDto(RoleRequest roleRequest) {
        com.cinasee.componente3_usuarios.dto.RoleRequest dto =
                new com.cinasee.componente3_usuarios.dto.RoleRequest(
                        roleRequest.getId(),
                        roleRequest.getUserId(),
                        com.cinasee.componente3_usuarios.dto.RoleRequest.RequestedRoleEnum.valueOf(
                                roleRequest.getRequestedRole().name()
                        ),
                        com.cinasee.componente3_usuarios.dto.RoleRequest.StatusEnum.valueOf(
                                roleRequest.getStatus().name()
                        ),
                        roleRequest.getCreatedAt()
                );

        dto.setCinemaId(roleRequest.getCinemaId());
        dto.setMotivation(roleRequest.getMotivation());
        dto.setReviewedAt(roleRequest.getReviewedAt());
        dto.setReviewedBy(roleRequest.getReviewedBy());
        dto.setReviewComment(roleRequest.getReviewComment());
        return dto;
    }
}
