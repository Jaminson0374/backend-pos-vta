package co.posinvent.application.port.in;

import co.posinvent.application.dto.TransferResponse;

import java.util.UUID;

public interface CancelTransferPort {

    TransferResponse execute(UUID transferId);
}
