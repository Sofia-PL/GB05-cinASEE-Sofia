package com.cinasee.componente1_cartelera.repositories;

import com.cinasee.componente1_cartelera.models.Movie;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovieRepository extends MongoRepository<Movie, String> {
    List<Movie> findByGenreIgnoreCase(String genre);
}

