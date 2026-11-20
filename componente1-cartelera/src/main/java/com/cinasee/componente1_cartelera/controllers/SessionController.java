package com.cinasee.componente1_cartelera.controllers;

import com.cinasee.componente1_cartelera.models.CreateSessionRequest;
import com.cinasee.componente1_cartelera.models.CreateSessionBatchRequest;
import com.cinasee.componente1_cartelera.models.Session;
import com.cinasee.componente1_cartelera.models.SessionBatchResponse;
import com.cinasee.componente1_cartelera.models.UpdateSessionRequest;
import com.cinasee.componente1_cartelera.services.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @GetMapping
    public ResponseEntity<List<Session>> getSessions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String movieId) {
        List<Session> sessions = sessionService.getAllSessions(date, movieId);
        return ResponseEntity.ok(sessions);
    }

    @PostMapping
    public ResponseEntity<Session> createSession(@RequestBody @Valid CreateSessionRequest request) {
        Session createdSession = sessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSession);
    }

    @PostMapping("/batch")
    public ResponseEntity<SessionBatchResponse> createSessionsBatch(
            @RequestBody @Valid CreateSessionBatchRequest request) {
        return ResponseEntity.ok(sessionService.createSessionsBatch(request));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<Session> getSessionById(@PathVariable String sessionId) {
        return sessionService.getSessionById(sessionId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión no encontrada con ID: " + sessionId));
    }

    @PatchMapping("/{sessionId}")
    public ResponseEntity<Session> updateSessionStatus(
            @PathVariable String sessionId,
            @RequestBody @Valid UpdateSessionRequest request) {
        Session updatedSession = sessionService.updateSessionStatus(sessionId, request);
        return ResponseEntity.ok(updatedSession);
    }
}

