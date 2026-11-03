package com.cinasee.componente1_cartelera.repositories;

import com.cinasee.componente1_cartelera.models.Room;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomRepository extends MongoRepository<Room, Long> {
}

