package co.posinvent.application.usecase;

import co.posinvent.application.dto.DesposteResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.port.in.DesposteQueryPort;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.repository.DesposteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class ListDespostesUseCase implements DesposteQueryPort {

    private final DesposteRepository desposteRepository;

    public ListDespostesUseCase(DesposteRepository desposteRepository) {
        this.desposteRepository = desposteRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DesposteResponse> findPage(LocalDate from, LocalDate to, int page, int size) {
        return PageResponse.from(
                desposteRepository.findPage(from, to, page, size),
                DesposteResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public DesposteResponse findById(UUID id) {
        return desposteRepository.findById(id)
                .map(DesposteResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Desposte", id));
    }
}
