package co.posinvent.application.port.in;

import co.posinvent.application.dto.SlaughterRequest;
import co.posinvent.application.dto.SlaughterResponse;

import java.util.UUID;

public interface ProcessSlaughterPort {

    SlaughterResponse process(SlaughterRequest request, UUID operatorId);
}
