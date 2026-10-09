package co.posinvent.application.port.in;

import co.posinvent.application.dto.TransferRequest;
import co.posinvent.application.dto.TransferResponse;

public interface CreateTransferPort {

    TransferResponse execute(TransferRequest request);
}
