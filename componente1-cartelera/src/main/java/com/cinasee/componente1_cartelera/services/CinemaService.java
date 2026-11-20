package com.cinasee.componente1_cartelera.services;

import com.cinasee.componente1_cartelera.models.Cinema;
import com.cinasee.componente1_cartelera.models.CreateCinemaRequest;
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
public class CinemaService {

    private final CinemaRepository cinemaRepository;
    private final RoomRepository roomRepository;

    public List<Cinema> getAllCinemas(String city) {
        if (city != null && !city.isBlank()) {
            return cinemaRepository.findByCityIgnoreCase(city);
        }
        return cinemaRepository.findAll();
    }

    public Optional<Cinema> getCinemaById(String cinemaId) {
        return cinemaRepository.findById(cinemaId);
    }

    public Cinema createCinema(CreateCinemaRequest request) {
        Cinema cinema = Cinema.builder()
                .id(java.util.UUID.randomUUID().toString())
                .name(request.getName())
                .address(request.getAddress())
                .city(request.getCity())
                .phone(request.getPhone())
                .build();
        return cinemaRepository.save(cinema);
    }

    public Cinema updateCinema(String cinemaId, CreateCinemaRequest request) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cine no encontrado con ID: " + cinemaId));

        cinema.setName(request.getName());
        cinema.setAddress(request.getAddress());
        cinema.setCity(request.getCity());
        cinema.setPhone(request.getPhone());

        return cinemaRepository.save(cinema);
    }

    public void deleteCinema(String cinemaId) {
        if (!cinemaRepository.existsById(cinemaId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cine no encontrado con ID: " + cinemaId);
        }
        if (!roomRepository.findByCinemaId(cinemaId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar el cine porque tiene salas asociadas.");
        }
        cinemaRepository.deleteById(cinemaId);
    }
}
