package co.posinvent.domain.model;

import java.util.UUID;

public record Tax(UUID id, String code, String name, int sortOrder) {}
