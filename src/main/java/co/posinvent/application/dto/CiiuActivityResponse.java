package co.posinvent.application.dto;

import co.posinvent.domain.model.CiiuActivity;

public record CiiuActivityResponse(String code, String name) {

    public static CiiuActivityResponse from(CiiuActivity a) {
        return new CiiuActivityResponse(a.code(), a.name());
    }
}
