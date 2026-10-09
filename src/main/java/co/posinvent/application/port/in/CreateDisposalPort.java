package co.posinvent.application.port.in;

import co.posinvent.application.dto.DisposalRequest;
import co.posinvent.application.dto.DisposalResponse;

public interface CreateDisposalPort {

    DisposalResponse execute(DisposalRequest request);
}
