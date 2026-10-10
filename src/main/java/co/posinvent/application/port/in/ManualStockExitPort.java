package co.posinvent.application.port.in;

import co.posinvent.application.dto.ManualStockExitRequest;

public interface ManualStockExitPort {

    void execute(ManualStockExitRequest request);
}
