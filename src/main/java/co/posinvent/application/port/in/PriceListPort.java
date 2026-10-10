package co.posinvent.application.port.in;

import co.posinvent.application.dto.PriceListRequest;
import co.posinvent.application.dto.PriceListResponse;

import java.util.List;
import java.util.UUID;

public interface PriceListPort {

    List<PriceListResponse> listAll();

    PriceListResponse create(PriceListRequest request);

    PriceListResponse update(UUID id, PriceListRequest request);

    void deactivate(UUID id);
}
