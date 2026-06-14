package co.posinvent.application.dto;

import co.posinvent.domain.model.ProductGroup;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductGroupResponse(
    UUID id,
    String name,
    UUID categoryId,
    boolean active,
    OffsetDateTime createdAt,
    UUID accountingTemplateId
) {
    public static ProductGroupResponse from(ProductGroup d) {
        return new ProductGroupResponse(d.id(), d.name(), d.categoryId(), d.active(), d.createdAt(), d.accountingTemplateId());
    }
}
