package co.posinvent.application.port.in;

import co.posinvent.application.dto.TransferResponse;

import java.util.UUID;

public interface GetTransferPort {

    TransferResponse execute(UUID id);
}
