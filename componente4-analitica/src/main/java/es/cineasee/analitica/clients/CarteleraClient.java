package es.cineasee.analitica.clients;

import es.cineasee.analitica.clients.dto.MovieDTO;
import es.cineasee.analitica.clients.dto.RoomDTO;
import es.cineasee.analitica.clients.dto.SessionDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "cartelera-service", url = "${services.cartelera.url:http://localhost:8081/api/v1}")
public interface CarteleraClient {

    @GetMapping("/movies")
    List<MovieDTO> getMovies(
            @RequestParam(value = "genre", required = false) String genre,
            @RequestParam(value = "inShowcase", required = false) Boolean inShowcase
    );

    @GetMapping("/movies/{movieId}")
    MovieDTO getMovieById(@PathVariable("movieId") Long movieId);

    @GetMapping("/rooms")
    List<RoomDTO> getAllRooms();

    @GetMapping("/rooms/{roomId}")
    RoomDTO getRoomById(@PathVariable("roomId") Long roomId);

    @GetMapping("/sessions")
    List<SessionDTO> getSessions(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "movieId", required = false) Long movieId
    );

    @GetMapping("/sessions/{sessionId}")
    SessionDTO getSessionById(@PathVariable("sessionId") Long sessionId);
}

