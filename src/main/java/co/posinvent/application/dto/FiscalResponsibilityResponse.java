package co.posinvent.application.dto;

import co.posinvent.domain.model.FiscalResponsibility;

import java.util.List;
import java.util.UUID;

public record FiscalResponsibilityResponse(UUID id, String code, String name, List<String> excludes, int sortOrder) {
    public static FiscalResponsibilityResponse from(FiscalResponsibility r) {
        return new FiscalResponsibilityResponse(r.id(), r.code(), r.name(), r.excludes(), r.sortOrder());
    }
}
