package co.posinvent.application.dto;

import co.posinvent.domain.model.PucAccount;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PucAccountResponse(
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
) {
    public static PucAccountResponse from(PucAccount d) {
        return new PucAccountResponse(
            d.id(), d.code(), d.name(), d.level(), d.parentCode(),
            d.accountClass(), d.accountNature(), d.allowsTransactions(), d.active(),
            d.createdAt(), d.updatedAt()
        );
    }
}
