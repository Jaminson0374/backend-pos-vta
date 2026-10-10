package co.posinvent.application.port.in;

import co.posinvent.application.dto.AdjustmentRequest;
import co.posinvent.application.dto.AdjustmentResponse;

public interface CreateAdjustmentPort {

    AdjustmentResponse execute(AdjustmentRequest request);
}
