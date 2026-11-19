package com.cinasee.componente1_cartelera.repositories;

import com.cinasee.componente1_cartelera.models.Cinema;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CinemaRepository extends MongoRepository<Cinema, String> {
    List<Cinema> findByCityIgnoreCase(String city);
}
