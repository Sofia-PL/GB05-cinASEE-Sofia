package com.cinasee.componente2_reservas.services;

import com.cinasee.componente2_reservas.exceptions.ForbiddenOperationException;
import com.cinasee.componente2_reservas.models.Reservation;
import com.cinasee.componente2_reservas.security.CallerIdentity;

/**
 * Regla de autorización sobre una reserva concreta (control de acceso a nivel de recurso).
 *
 * Puede consultarla, pagarla o cancelarla:
 * <ul>
 *   <li>Personal de taquilla o administración (cualquier reserva).</li>
 *   <li>Quien la creó (mismo "sub" en el JWT, incluido el invitado con su token).</li>
 *   <li>El cliente registrado al que pertenece, aunque la creara la taquilla.</li>
 * </ul>
 */
public final class ReservationAccessPolicy {

    private ReservationAccessPolicy() {
    }

    public static void checkCanAccess(Reservation reservation, CallerIdentity caller) {
        if (caller.isStaff()) {
            return;
        }
        if (reservation.getOwnerSubject().equals(caller.subject())) {
            return;
        }
        boolean isOwnerUser = caller.isRegisteredClient()
                && reservation.getUserId() != null
                && caller.userId().map(reservation.getUserId()::equals).orElse(false);
        if (isOwnerUser) {
            return;
        }
        throw new ForbiddenOperationException("No tienes permiso sobre la reserva " + reservation.getId());
    }
}
