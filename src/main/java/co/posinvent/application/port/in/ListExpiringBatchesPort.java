package co.posinvent.application.port.in;

import java.util.List;
import java.util.Map;

public interface ListExpiringBatchesPort {

    List<Map<String, Object>> execute(int days);
}
