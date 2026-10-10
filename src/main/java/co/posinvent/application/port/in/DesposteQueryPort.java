package co.posinvent.application.port.in;

import co.posinvent.application.dto.DesposteResponse;
import co.posinvent.application.dto.PageResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface DesposteQueryPort {

    PageResponse<DesposteResponse> findPage(LocalDate from, LocalDate to, int page, int size);

    DesposteResponse findById(UUID id);
}
