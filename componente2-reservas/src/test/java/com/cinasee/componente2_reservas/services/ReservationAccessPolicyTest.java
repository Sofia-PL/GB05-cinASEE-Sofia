package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.TestData;
import com.cinasee.componente2_reservas.exceptions.ForbiddenOperationException;
import com.cinasee.componente2_reservas.models.Reservation;
import com.cinasee.componente2_reservas.security.CallerIdentity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitarios de la regla de acceso a nivel de reserva.
 */
class ReservationAccessPolicyTest {

    private static Reservation reservation(String ownerSubject, Long userId) {
        return Reservation.builder().id(42L).ownerSubject(ownerSubject).userId(userId).build();
    }

    @Test
    void staffAndAdminCanAccessAnyReservation() {
        Reservation other = reservation("9999", 9999L);
        CallerIdentity admin = new CallerIdentity("2", CallerIdentity.ROLE_ADMIN);

        assertThatCode(() -> ReservationAccessPolicy.checkCanAccess(other, TestData.STAFF)).doesNotThrowAnyException();
        assertThatCode(() -> ReservationAccessPolicy.checkCanAccess(other, admin)).doesNotThrowAnyException();
    }

    @Test
    void creatorCanAccessOwnReservation() {
        Reservation own = reservation(TestData.CLIENT.subject(), TestData.USER_ID);

        assertThatCode(() -> ReservationAccessPolicy.checkCanAccess(own, TestData.CLIENT)).doesNotThrowAnyException();
    }

    @Test
    void guestCanAccessReservationCreatedWithItsToken() {
        Reservation guestReservation = reservation(TestData.GUEST.subject(), null);

        assertThatCode(() -> ReservationAccessPolicy.checkCanAccess(guestReservation, TestData.GUEST))
                .doesNotThrowAnyException();
    }

    @Test
    void registeredClientCanAccessReservationCreatedForThemByBoxOffice() {
        // La creó la taquilla (sub "1") pero pertenece al cliente 5590
        Reservation boxOffice = reservation(TestData.STAFF.subject(), TestData.USER_ID);

        assertThatCode(() -> ReservationAccessPolicy.checkCanAccess(boxOffice, TestData.CLIENT))
                .doesNotThrowAnyException();
    }

    @Test
    void otherClientIsForbidden() {
        Reservation own = reservation(TestData.CLIENT.subject(), TestData.USER_ID);

        assertThatThrownBy(() -> ReservationAccessPolicy.checkCanAccess(own, TestData.OTHER_CLIENT))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("42");
    }

    @Test
    void otherGuestIsForbidden() {
        Reservation guestReservation = reservation(TestData.GUEST.subject(), null);
        CallerIdentity anotherGuest = new CallerIdentity("guest:otro", CallerIdentity.ROLE_GUEST);

        assertThatThrownBy(() -> ReservationAccessPolicy.checkCanAccess(guestReservation, anotherGuest))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void onlyRegisteredClientsBenefitFromUserIdRule() {
        // Un token que no es CLIENTE_REGISTRADO no accede aunque su "sub" coincida con el userId
        Reservation boxOffice = reservation(TestData.STAFF.subject(), TestData.USER_ID);
        CallerIdentity notAClient = new CallerIdentity(String.valueOf(TestData.USER_ID), CallerIdentity.ROLE_GUEST);

        assertThatThrownBy(() -> ReservationAccessPolicy.checkCanAccess(boxOffice, notAClient))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
