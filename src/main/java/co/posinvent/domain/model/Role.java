package co.posinvent.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Role(
    UUID id,
    String name,
    OffsetDateTime createdAt
) {}
