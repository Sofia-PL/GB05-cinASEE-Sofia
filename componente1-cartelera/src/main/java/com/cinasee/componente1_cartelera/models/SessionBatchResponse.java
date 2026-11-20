package com.cinasee.componente1_cartelera.models;

import java.time.LocalDateTime;
import java.util.List;

public record SessionBatchResponse(
        List<Session> created,
        List<Conflict> conflicts
) {
    public record Conflict(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String conflictingSessionId
    ) {
    }
}
