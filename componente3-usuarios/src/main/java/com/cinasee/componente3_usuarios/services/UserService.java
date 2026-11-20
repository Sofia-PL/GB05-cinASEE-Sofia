package com.cinasee.componente3_usuarios.services;

import com.cinasee.componente3_usuarios.dto.AssignRoleRequest;
import com.cinasee.componente3_usuarios.dto.PurchaseHistoryItem;
import com.cinasee.componente3_usuarios.dto.UpdateUserProfileRequest;
import com.cinasee.componente3_usuarios.dto.UserProfile;

import java.util.List;

public interface UserService {

    UserProfile getUserProfile(Long userId);

    void updateUserProfile(Long userId, UpdateUserProfileRequest request);

    List<PurchaseHistoryItem> getUserHistory(Long userId);

    void assignRole(Long userId, AssignRoleRequest request);
}
