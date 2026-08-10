package co.posinvent.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PucAccount(
    UUID id,
    String code,
    String name,
    int level,
    String parentCode,
    int accountClass,
    String accountNature,
    boolean allowsTransactions,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
