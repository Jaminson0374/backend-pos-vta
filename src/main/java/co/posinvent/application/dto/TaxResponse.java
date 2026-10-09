package co.posinvent.application.dto;

import co.posinvent.domain.model.Tax;

import java.util.UUID;

public record TaxResponse(UUID id, String code, String name, int sortOrder) {
    public static TaxResponse from(Tax t) {
        return new TaxResponse(t.id(), t.code(), t.name(), t.sortOrder());
    }
}
