package com.cinasee.componente1_cartelera.services;

import com.cinasee.componente1_cartelera.models.CreateRoomRequest;
import com.cinasee.componente1_cartelera.models.Room;
import com.cinasee.componente1_cartelera.models.Session;
import com.cinasee.componente1_cartelera.repositories.CinemaRepository;
import com.cinasee.componente1_cartelera.repositories.RoomRepository;
import com.cinasee.componente1_cartelera.repositories.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final CinemaRepository cinemaRepository;
    private final SessionRepository sessionRepository;

    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    public List<Room> getRoomsByCinemaId(String cinemaId) {
        return roomRepository.findByCinemaId(cinemaId);
    }

    public Optional<Room> getRoomById(String roomId) {
        return roomRepository.findById(roomId);
    }

    public Room createRoom(CreateRoomRequest request) {
        validateCinema(request.getCinemaId());
        Room room = Room.builder()
                .id(java.util.UUID.randomUUID().toString())
                .cinemaId(request.getCinemaId())
                .name(request.getName())
                .roomType(request.getRoomType())
                .rowsCount(request.getRowsCount())
                .columnsCount(request.getColumnsCount())
                .capacity(request.getRowsCount() * request.getColumnsCount())
                .wheelchairAccessible(request.getWheelchairAccessible() != null ? request.getWheelchairAccessible() : false)
                .basePrice(request.getBasePrice() != null ? request.getBasePrice() : 0.0)
                .build();

        return roomRepository.save(room);
    }

    public Room updateRoom(String roomId, CreateRoomRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sala no encontrada con ID: " + roomId));
        validateCinema(request.getCinemaId());

        room.setCinemaId(request.getCinemaId());
        room.setName(request.getName());
        room.setRoomType(request.getRoomType());
        room.setRowsCount(request.getRowsCount());
        room.setColumnsCount(request.getColumnsCount());
        room.setCapacity(request.getRowsCount() * request.getColumnsCount());
        room.setWheelchairAccessible(request.getWheelchairAccessible() != null ? request.getWheelchairAccessible() : false);
        room.setBasePrice(request.getBasePrice() != null ? request.getBasePrice() : 0.0);
        return roomRepository.save(room);
    }

    public void deleteRoom(String roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sala no encontrada con ID: " + roomId);
        }

        LocalDateTime now = LocalDateTime.now();
        boolean hasFutureSessions = sessionRepository.findByRoomIdAndStatusNot(roomId, "CANCELADA").stream()
                .map(Session::getStartTime)
                .anyMatch(startTime -> startTime != null && startTime.isAfter(now));
        if (hasFutureSessions) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar la sala porque tiene sesiones futuras.");
        }

        roomRepository.deleteById(roomId);
    }

    private void validateCinema(String cinemaId) {
        if (!cinemaRepository.existsById(cinemaId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cine no encontrado con ID: " + cinemaId);
        }
    }
}

