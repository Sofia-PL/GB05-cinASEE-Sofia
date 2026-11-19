package com.cinasee.componente1_cartelera.repositories;

import com.cinasee.componente1_cartelera.models.Room;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends MongoRepository<Room, String> {
    List<Room> findByCinemaId(String cinemaId);
}

