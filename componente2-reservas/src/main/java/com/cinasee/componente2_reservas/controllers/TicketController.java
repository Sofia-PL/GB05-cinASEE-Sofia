package com.cinasee.componente2_reservas.controllers;

import com.cinasee.componente2_reservas.dto.CheckInResponse;
import com.cinasee.componente2_reservas.services.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal de validación en puerta: el empleado escanea el QR y se marca la entrada
 * como usada. Solo ADMIN y STAFF_TAQUILLA (ver {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/reservations/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping("/{ticketCode}/check-in")
    public CheckInResponse checkIn(@PathVariable String ticketCode) {
        return ticketService.checkIn(ticketCode);
    }
}
