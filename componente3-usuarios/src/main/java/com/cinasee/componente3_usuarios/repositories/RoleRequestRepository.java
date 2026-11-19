package com.cinasee.componente3_usuarios.repositories;

import com.cinasee.componente3_usuarios.models.Role;
import com.cinasee.componente3_usuarios.models.RoleRequest;
import com.cinasee.componente3_usuarios.models.RoleRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleRequestRepository extends JpaRepository<RoleRequest, Long> {

    boolean existsByUserIdAndStatus(Long userId, RoleRequestStatus status);

    List<RoleRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<RoleRequest> findAllByOrderByCreatedAtDesc();

    List<RoleRequest> findByStatusOrderByCreatedAtDesc(RoleRequestStatus status);

    List<RoleRequest> findByCinemaIdOrderByCreatedAtDesc(String cinemaId);

    List<RoleRequest> findByCinemaIdAndStatusOrderByCreatedAtDesc(String cinemaId, RoleRequestStatus status);

    List<RoleRequest> findByCinemaIdAndRequestedRoleOrderByCreatedAtDesc(String cinemaId, Role requestedRole);

    List<RoleRequest> findByCinemaIdAndRequestedRoleAndStatusOrderByCreatedAtDesc(
            String cinemaId,
            Role requestedRole,
            RoleRequestStatus status
    );
}
