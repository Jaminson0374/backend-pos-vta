package co.posinvent.application.port.in;

import co.posinvent.application.dto.ManualStockEntryRequest;

public interface ManualStockEntryPort {

    void execute(ManualStockEntryRequest request);
}
