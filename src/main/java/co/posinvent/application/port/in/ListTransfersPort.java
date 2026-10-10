package co.posinvent.application.port.in;

import co.posinvent.application.dto.TransferResponse;
import org.springframework.data.domain.Page;

public interface ListTransfersPort {

    Page<TransferResponse> execute(int page, int size);
}
