package co.posinvent.application.port.in;

import java.util.UUID;

public interface DisposeBatchPort {

    void disposeBatch(UUID batchId);
}
