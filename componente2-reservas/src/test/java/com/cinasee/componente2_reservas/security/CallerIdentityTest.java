package com.cinasee.componente2_reservas.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios de la identidad extraída del JWT: roles y userId.
 */
class CallerIdentityTest {

    @Test
    void fromJwtExtractsSubjectAndRole() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("5590")
                .claim("role", CallerIdentity.ROLE_CLIENT)
                .issuedAt(Instant.parse("2026-10-08T12:00:00Z"))
                .expiresAt(Instant.parse("2026-10-08T13:00:00Z"))
                .build();

        CallerIdentity caller = CallerIdentity.from(jwt);

        assertThat(caller.subject()).isEqualTo("5590");
        assertThat(caller.role()).isEqualTo(CallerIdentity.ROLE_CLIENT);
    }

    @Test
    void staffAndAdminAreStaff() {
        assertThat(new CallerIdentity("1", CallerIdentity.ROLE_STAFF).isStaff()).isTrue();
        assertThat(new CallerIdentity("1", CallerIdentity.ROLE_ADMIN).isStaff()).isTrue();
        assertThat(new CallerIdentity("1", CallerIdentity.ROLE_CLIENT).isStaff()).isFalse();
        assertThat(new CallerIdentity("guest:abc", CallerIdentity.ROLE_GUEST).isStaff()).isFalse();
    }

    @Test
    void roleChecksAreExclusive() {
        CallerIdentity guest = new CallerIdentity("guest:abc", CallerIdentity.ROLE_GUEST);
        CallerIdentity client = new CallerIdentity("5590", CallerIdentity.ROLE_CLIENT);

        assertThat(guest.isGuest()).isTrue();
        assertThat(guest.isRegisteredClient()).isFalse();
        assertThat(client.isRegisteredClient()).isTrue();
        assertThat(client.isGuest()).isFalse();
    }

    @Test
    void missingRoleHasNoPrivileges() {
        CallerIdentity noRole = new CallerIdentity("5590", null);

        assertThat(noRole.isStaff()).isFalse();
        assertThat(noRole.isGuest()).isFalse();
        assertThat(noRole.isRegisteredClient()).isFalse();
    }

    @Test
    void userIdIsParsedFromNumericSubject() {
        assertThat(new CallerIdentity("5590", CallerIdentity.ROLE_CLIENT).userId()).contains(5590L);
    }

    @Test
    void userIdIsEmptyForGuestOrNullSubject() {
        assertThat(new CallerIdentity("guest:3f2c7a1e", CallerIdentity.ROLE_GUEST).userId()).isEmpty();
        assertThat(new CallerIdentity(null, CallerIdentity.ROLE_GUEST).userId()).isEmpty();
    }
}
