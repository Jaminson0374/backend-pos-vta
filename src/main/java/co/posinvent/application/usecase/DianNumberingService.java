package co.posinvent.application.usecase;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.DianResolution;
import co.posinvent.domain.repository.DianResolutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Issues the next fiscal invoice number from the ACTIVE DIAN resolution.
 *
 * <p>The active resolution row is loaded with a pessimistic write lock so
 * concurrent checkouts serialize on the consecutive number: each caller reads
 * the committed {@code currentNumber}, increments it, persists the new value and
 * only then releases the lock. This replaces the old timestamp-based numbering
 * ({@code INVOICE-yyyyMMdd-<millis>}) that ignored the configured resolution.</p>
 */
@Service
public class DianNumberingService {

    private final DianResolutionRepository repository;

    public DianNumberingService(DianResolutionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String nextInvoiceNumber() {
        var resolution = repository.findActiveForUpdate()
                .orElseThrow(() -> new BusinessException(
                        "DIAN_NO_ACTIVE_RESOLUTION",
                        "No hay una resolución DIAN activa configurada"));

        var today = LocalDate.now();
        if (today.isBefore(resolution.validFrom()) || today.isAfter(resolution.validTo())) {
            throw new BusinessException("DIAN_RESOLUTION_EXPIRED",
                    "La resolución DIAN activa no está vigente");
        }

        long rangeFrom = resolution.rangeFrom();
        long rangeTo = resolution.rangeTo();

        long base = resolution.currentNumber() != null ? resolution.currentNumber() : rangeFrom - 1;
        long next = Math.max(base + 1, rangeFrom);

        if (next > rangeTo) {
            throw new BusinessException("DIAN_RANGE_EXHAUSTED",
                    "Se agotó el rango autorizado de la resolución DIAN");
        }

        var updated = new DianResolution(
                resolution.id(),
                resolution.resolutionNumber(),
                resolution.resolutionDate(),
                resolution.validFrom(),
                resolution.validTo(),
                resolution.prefix(),
                resolution.rangeFrom(),
                resolution.rangeTo(),
                next,
                resolution.softwarePin(),
                resolution.active(),
                resolution.createdAt()
        );
        repository.save(updated);

        int width = String.valueOf(rangeTo).length();
        return normalizePrefix(resolution.prefix()) + zeroPadded(next, width);
    }

    private static String zeroPadded(long value, int width) {
        return String.format("%0" + Math.max(width, 1) + "d", value);
    }

    private static String normalizePrefix(String prefix) {
        return prefix != null ? prefix : "";
    }
}
