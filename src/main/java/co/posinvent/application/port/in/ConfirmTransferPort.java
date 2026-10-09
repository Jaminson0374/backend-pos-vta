package co.posinvent.application.port.in;

import co.posinvent.application.dto.TransferResponse;

import java.util.UUID;

public interface ConfirmTransferPort {

    TransferResponse execute(UUID transferId);
}
