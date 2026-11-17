package com.cinasee.componente3_usuarios.services;

import com.cinasee.componente3_usuarios.dto.PurchaseHistoryItem;
import com.cinasee.componente3_usuarios.dto.UpdateUserProfileRequest;
import com.cinasee.componente3_usuarios.dto.UserProfile;
import com.cinasee.componente3_usuarios.models.PurchaseHistory;
import com.cinasee.componente3_usuarios.models.Role;
import com.cinasee.componente3_usuarios.models.User;
import com.cinasee.componente3_usuarios.repositories.PurchaseHistoryRepository;
import com.cinasee.componente3_usuarios.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PurchaseHistoryRepository purchaseHistoryRepository;

    @Override
    @Transactional(readOnly = true)
    public UserProfile getUserProfile(Long userId) {
        User user = findUser(userId);

        UserProfile profile = new UserProfile(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getLoyaltyPoints()
        );

        List<String> genres = user.getPreferredGenres() == null
                ? List.of()
                : new ArrayList<>(user.getPreferredGenres());
        profile.setPreferredGenres(genres);
        profile.setPhone(user.getPhone());
        profile.setHomeCinema(user.getHomeCinema());
        profile.setCinemaId(user.getCinemaId());
        profile.setCanReviewRoleRequests(canReviewRoleRequests(user));

        return profile;
    }

    @Override
    @Transactional
    public void updateUserProfile(Long userId, UpdateUserProfileRequest request) {
        User user = findUser(userId);

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }

        if (request.getPreferredGenres() != null) {
            List<String> genres = request.getPreferredGenres().stream()
                    .filter(genre -> genre != null && !genre.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
            user.setPreferredGenres(new ArrayList<>(genres));
        }

        if (request.getPhone() != null) {
            String phone = request.getPhone().trim();
            user.setPhone(phone.isEmpty() ? null : phone);
        }

        if (request.getHomeCinema() != null) {
            String homeCinema = request.getHomeCinema().trim();
            user.setHomeCinema(homeCinema.isEmpty() ? null : homeCinema);
        }

        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseHistoryItem> getUserHistory(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Usuario no encontrado con ID: " + userId
            );
        }

        return purchaseHistoryRepository.findByUserIdOrderByPurchaseDateDesc(userId).stream()
                .map(this::toPurchaseHistoryItem)
                .toList();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Usuario no encontrado con ID: " + userId
                ));
    }

    private boolean canReviewRoleRequests(User user) {
        return user.getRole() == Role.ADMIN
                || (user.getRole() == Role.STAFF_TAQUILLA && user.isCinemaManager());
    }

    private PurchaseHistoryItem toPurchaseHistoryItem(PurchaseHistory history) {
        return new PurchaseHistoryItem(
                history.getReservationId(),
                history.getMovieId(),
                history.getPurchaseDate(),
                history.getAmount().floatValue()
        );
    }
}
