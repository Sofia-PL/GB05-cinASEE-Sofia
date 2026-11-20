package com.cinasee.componente1_cartelera.controllers;

import com.cinasee.componente1_cartelera.models.CreateRoomRequest;
import com.cinasee.componente1_cartelera.models.Room;
import com.cinasee.componente1_cartelera.services.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public ResponseEntity<List<Room>> getRooms(
            @RequestParam(required = false) String cinemaId) {
        List<Room> rooms;
        if (cinemaId != null) {
            rooms = roomService.getRoomsByCinemaId(cinemaId);
        } else {
            rooms = roomService.getAllRooms();
        }
        return ResponseEntity.ok(rooms);
    }

    @PostMapping
    public ResponseEntity<Room> createRoom(@RequestBody @Valid CreateRoomRequest request) {
        Room createdRoom = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdRoom);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<Room> getRoomById(@PathVariable String roomId) {
        return roomService.getRoomById(roomId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sala no encontrada con ID: " + roomId));
    }

    @PutMapping("/{roomId}")
    public ResponseEntity<Room> updateRoom(@PathVariable String roomId, @RequestBody @Valid CreateRoomRequest request) {
        Room updatedRoom = roomService.updateRoom(roomId, request);
        return ResponseEntity.ok(updatedRoom);
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId) {
        roomService.deleteRoom(roomId);
        return ResponseEntity.noContent().build();
    }
}

