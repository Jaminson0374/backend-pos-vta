package co.posinvent.application.usecase;

import co.posinvent.application.dto.CashCountLineRequest;
import co.posinvent.application.dto.CashCountResponse;
import co.posinvent.application.dto.RegisterCashCountRequest;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.CashCount;
import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.repository.CashCountRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.domain.repository.ShiftRepository;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Persists a historical cash count (arqueo) for a shift.
 *
 * <p>The POS screen counts denominations in memory and previously discarded them.
 * This use case turns those lines into a {@link CashCount} record: the counted total
 * is the sum of {@code denominationValue * quantity}, the expected total is the NET
 * shift total (invoices plus negative credit notes — audit A2), and the difference is
 * {@code countedTotal - expectedTotal}.</p>
 */
@Service
public class RegisterCashCountUseCase {

    private final ShiftRepository shiftRepository;
    private final SalesDocumentRepository documentRepo;
    private final CashCountRepository cashCountRepository;
    private final CurrentActorProvider actorProvider;
    private final ShiftAccessGuard accessGuard;

    public RegisterCashCountUseCase(
            ShiftRepository shiftRepository,
            SalesDocumentRepository documentRepo,
            CashCountRepository cashCountRepository,
            CurrentActorProvider actorProvider,
            ShiftAccessGuard accessGuard
    ) {
        this.shiftRepository = shiftRepository;
        this.documentRepo = documentRepo;
        this.cashCountRepository = cashCountRepository;
        this.actorProvider = actorProvider;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public CashCountResponse execute(UUID shiftId, RegisterCashCountRequest request) {
        var shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Turno", shiftId));

        var actor = actorProvider.current();
        accessGuard.requireOperate(shift, actor);

        var lines = request.lines().stream()
                .map(RegisterCashCountUseCase::toLine)
                .toList();

        BigDecimal countedTotal = lines.stream()
                .map(CashCount.Line::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expectedTotal = expectedTotal(shift.id());

        BigDecimal difference = countedTotal.subtract(expectedTotal);

        var toPersist = new CashCount(
                null,
                shift.id(),
                expectedTotal,
                countedTotal,
                difference,
                request.notes(),
                actor.userId(),
                null,
                null,
                lines);

        var saved = cashCountRepository.save(toPersist);
        return CashCountResponse.from(saved);
    }

    /** History of cash counts for a shift, most recent first. */
    @Transactional(readOnly = true)
    public List<CashCountResponse> history(UUID shiftId) {
        var shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Turno", shiftId));

        accessGuard.requireRead(shift, actorProvider.current());

        return cashCountRepository.findByShiftId(shiftId).stream()
                .map(CashCountResponse::from)
                .toList();
    }

    /**
     * NET expected total for the shift: invoices plus credit notes. Credit notes store
     * a NEGATIVE {@code totalAmount} (see {@code PosDevolutionUseCase}), so adding their
     * sum yields invoiced minus returned.
     */
    private BigDecimal expectedTotal(UUID shiftId) {
        BigDecimal invoicedTotal = sumByType(shiftId, SalesDocumentType.INVOICE);
        BigDecimal creditNoteTotal = sumByType(shiftId, SalesDocumentType.CREDIT_NOTE);
        return invoicedTotal.add(creditNoteTotal);
    }

    private BigDecimal sumByType(UUID shiftId, SalesDocumentType type) {
        return documentRepo.findByShiftIdAndType(shiftId, type).stream()
                .map(SalesDocument::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static CashCount.Line toLine(CashCountLineRequest request) {
        BigDecimal subtotal = request.denominationValue()
                .multiply(BigDecimal.valueOf(request.quantity()));
        return new CashCount.Line(
                null,
                request.denominationValue(),
                request.denominationType(),
                request.quantity(),
                subtotal);
    }
}
