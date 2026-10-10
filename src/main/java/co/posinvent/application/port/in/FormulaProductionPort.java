package co.posinvent.application.port.in;

import co.posinvent.application.dto.ProduceRequest;
import co.posinvent.application.dto.ProduceResponse;

import java.util.UUID;

public interface FormulaProductionPort {

    ProduceResponse produce(ProduceRequest request, UUID operatorId);
}
