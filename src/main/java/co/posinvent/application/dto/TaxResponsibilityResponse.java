package co.posinvent.application.dto;

import co.posinvent.domain.model.TaxResponsibility;

import java.util.List;
import java.util.UUID;

public record TaxResponsibilityResponse(UUID id, String code, String name, List<String> excludes, int sortOrder) {
    public static TaxResponsibilityResponse from(TaxResponsibility r) {
        return new TaxResponsibilityResponse(r.id(), r.code(), r.name(), r.excludes(), r.sortOrder());
    }
}
