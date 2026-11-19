package com.cinasee.componente3_usuarios.config;

import com.cinasee.componente3_usuarios.models.Role;
import com.cinasee.componente3_usuarios.models.User;
import com.cinasee.componente3_usuarios.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Crea los usuarios de demostración la primera vez que arranca el servicio.
 *
 * <p>Es idempotente: si el email ya existe, no modifica nada. En producción se
 * desactiva con {@code APP_SEED_ENABLED=false}.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final String adminEmail;
    private final String adminPassword;
    private final String staffEmail;
    private final String staffPassword;
    private final String userEmail;
    private final String userPassword;
    private final String staffCinemaId;

    public DemoDataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.admin-email:admin@cinasee.com}") String adminEmail,
            @Value("${app.seed.admin-password:Admin123!}") String adminPassword,
            @Value("${app.seed.staff-email:staff@cinasee.com}") String staffEmail,
            @Value("${app.seed.staff-password:Staff123!}") String staffPassword,
            @Value("${app.seed.user-email:usuario@cinasee.com}") String userEmail,
            @Value("${app.seed.user-password:Usuario123!}") String userPassword,
            @Value("${app.seed.staff-cinema-id:1}") String staffCinemaId
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.staffEmail = staffEmail;
        this.staffPassword = staffPassword;
        this.userEmail = userEmail;
        this.userPassword = userPassword;
        this.staffCinemaId = staffCinemaId;
    }

    @Override
    public void run(String... args) {
        createIfMissing(adminEmail, adminPassword, "Administrador CinAsee", Role.ADMIN, null, false);
        createIfMissing(staffEmail, staffPassword, "Taquillero CinAsee", Role.STAFF_TAQUILLA, staffCinemaId, true);
        createIfMissing(userEmail, userPassword, "Usuario Demo", Role.CLIENTE_REGISTRADO, null, false);
    }

    private void createIfMissing(
            String email,
            String password,
            String fullName,
            Role role,
            String cinemaId,
            boolean cinemaManager
    ) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            log.debug("Usuario semilla {} ya existe; no se modifica", normalizedEmail);
            return;
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(fullName)
                .role(role)
                .loyaltyPoints(0)
                .preferredGenres(new ArrayList<>())
                .cinemaId(cinemaId)
                .cinemaManager(cinemaManager)
                .build();

        userRepository.save(user);
        log.info("Usuario semilla creado: {} ({})", normalizedEmail, role);
    }
}
