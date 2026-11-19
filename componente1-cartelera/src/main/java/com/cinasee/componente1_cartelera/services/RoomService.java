package com.cinasee.componente1_cartelera.services;

import com.cinasee.componente1_cartelera.models.CreateRoomRequest;
import com.cinasee.componente1_cartelera.models.Room;
import com.cinasee.componente1_cartelera.repositories.CinemaRepository;
import com.cinasee.componente1_cartelera.repositories.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final CinemaRepository cinemaRepository;

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
        // Validar que el cine existe
        if (!cinemaRepository.existsById(request.getCinemaId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cine no encontrado con ID: " + request.getCinemaId());
        }

        int capacity = request.getRowsCount() * request.getColumnsCount();
        Room room = Room.builder()
                .id(java.util.UUID.randomUUID().toString())
                .cinemaId(request.getCinemaId())
                .name(request.getName())
                .roomType(request.getRoomType())
                .rowsCount(request.getRowsCount())
                .columnsCount(request.getColumnsCount())
                .capacity(capacity)
                .wheelchairAccessible(request.getWheelchairAccessible() != null ? request.getWheelchairAccessible() : false)
                .basePrice(request.getBasePrice() != null ? request.getBasePrice() : 0.0)
                .build();

        return roomRepository.save(room);
    }
}

