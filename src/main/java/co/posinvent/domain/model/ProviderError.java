package co.posinvent.domain.model;

import java.util.List;

/** One validation error returned by Siigo for a rejected invoice payload. */
public record ProviderError(
        String code,
        String message,
        List<String> params,
        String detail
) {
    public ProviderError {
        if (params == null) params = List.of();
    }
}
