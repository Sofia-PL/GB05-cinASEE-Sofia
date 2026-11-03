package com.cinasee.componente1_cartelera.services;

import com.cinasee.componente1_cartelera.models.CreateRoomRequest;
import com.cinasee.componente1_cartelera.models.Room;
import com.cinasee.componente1_cartelera.repositories.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    public Optional<Room> getRoomById(Long roomId) {
        return roomRepository.findById(roomId);
    }

    public Room createRoom(CreateRoomRequest request) {
        int capacity = request.getRowsCount() * request.getColumnsCount();
        Long nextId = System.currentTimeMillis();

        Room room = Room.builder()
                .id(nextId)
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

