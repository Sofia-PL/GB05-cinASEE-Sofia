package com.cinasee.componente2_reservas.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del barrido periódico de reservas caducadas.
 */
@ExtendWith(MockitoExtension.class)
class ReservationExpirationSchedulerTest {

    @Mock
    private SeatLockService seatLockService;

    @InjectMocks
    private ReservationExpirationScheduler scheduler;

    @Test
    void sweepDelegatesToSeatLockService() {
        when(seatLockService.expireOverdueReservations()).thenReturn(3);

        scheduler.expireOverdueReservations();

        verify(seatLockService, times(1)).expireOverdueReservations();
    }

    @Test
    void sweepWithNothingToExpireDoesNotFail() {
        when(seatLockService.expireOverdueReservations()).thenReturn(0);

        assertThatCode(scheduler::expireOverdueReservations).doesNotThrowAnyException();
    }

    @Test
    void failureDoesNotPropagateSoSchedulerKeepsRunning() {
        when(seatLockService.expireOverdueReservations()).thenThrow(new IllegalStateException("BD caída"));

        assertThatCode(scheduler::expireOverdueReservations).doesNotThrowAnyException();
    }
}
