package com.cinasee.componente3_usuarios.services;

import com.cinasee.componente3_usuarios.dto.RoleRequestCreateRequest;
import com.cinasee.componente3_usuarios.dto.RoleRequestReviewRequest;

import java.util.List;

public interface RoleRequestService {

    com.cinasee.componente3_usuarios.dto.RoleRequest createRoleRequest(
            Long requesterId,
            RoleRequestCreateRequest request
    );

    List<com.cinasee.componente3_usuarios.dto.RoleRequest> getMyRoleRequests(Long requesterId);

    List<com.cinasee.componente3_usuarios.dto.RoleRequest> listRoleRequests(
            Long reviewerId,
            String status,
            String cinemaId
    );

    com.cinasee.componente3_usuarios.dto.RoleRequest reviewRoleRequest(
            Long reviewerId,
            Long requestId,
            RoleRequestReviewRequest request
    );
}
