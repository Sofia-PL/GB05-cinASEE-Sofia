package com.cinasee.componente1_cartelera.controllers;

import com.cinasee.componente1_cartelera.models.Cinema;
import com.cinasee.componente1_cartelera.models.CreateCinemaRequest;
import com.cinasee.componente1_cartelera.models.Room;
import com.cinasee.componente1_cartelera.services.CinemaService;
import com.cinasee.componente1_cartelera.services.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/cinemas")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaService cinemaService;
    private final RoomService roomService;

    @GetMapping
    public ResponseEntity<List<Cinema>> getCinemas(
            @RequestParam(required = false) String city) {
        List<Cinema> cinemas = cinemaService.getAllCinemas(city);
        return ResponseEntity.ok(cinemas);
    }

    @PostMapping
    public ResponseEntity<Cinema> createCinema(@RequestBody @Valid CreateCinemaRequest request) {
        Cinema createdCinema = cinemaService.createCinema(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCinema);
    }

    @GetMapping("/{cinemaId}")
    public ResponseEntity<Cinema> getCinemaById(@PathVariable String cinemaId) {
        return cinemaService.getCinemaById(cinemaId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cine no encontrado con ID: " + cinemaId));
    }

    @PutMapping("/{cinemaId}")
    public ResponseEntity<Cinema> updateCinema(@PathVariable String cinemaId, @RequestBody @Valid CreateCinemaRequest request) {
        Cinema updatedCinema = cinemaService.updateCinema(cinemaId, request);
        return ResponseEntity.ok(updatedCinema);
    }

    @DeleteMapping("/{cinemaId}")
    public ResponseEntity<Void> deleteCinema(@PathVariable String cinemaId) {
        cinemaService.deleteCinema(cinemaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{cinemaId}/rooms")
    public ResponseEntity<List<Room>> getRoomsByCinema(@PathVariable String cinemaId) {
        // Verificar que el cine existe
        cinemaService.getCinemaById(cinemaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cine no encontrado con ID: " + cinemaId));
        List<Room> rooms = roomService.getRoomsByCinemaId(cinemaId);
        return ResponseEntity.ok(rooms);
    }
}
