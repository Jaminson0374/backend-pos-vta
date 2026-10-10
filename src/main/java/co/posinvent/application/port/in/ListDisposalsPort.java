package co.posinvent.application.port.in;

import co.posinvent.application.dto.DisposalResponse;
import org.springframework.data.domain.Page;

public interface ListDisposalsPort {

    Page<DisposalResponse> execute(int page, int size);
}
