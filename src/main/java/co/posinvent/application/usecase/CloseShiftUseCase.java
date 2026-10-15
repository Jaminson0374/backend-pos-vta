package co.posinvent.application.usecase;

import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Shift;
import co.posinvent.domain.model.ShiftStatus;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.domain.repository.ShiftRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class CloseShiftUseCase {

    private final ShiftRepository shiftRepository;
    private final SalesDocumentRepository documentRepo;

    @Autowired
    public CloseShiftUseCase(
            ShiftRepository shiftRepository,
            SalesDocumentRepository documentRepo
    ) {
        this.shiftRepository = shiftRepository;
        this.documentRepo = documentRepo;
    }

    @Transactional
    public ShiftResponse execute(UUID shiftId, CashCountRequest cashCount) {
        var existing = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Turno", shiftId));

        if (existing.status() == ShiftStatus.CLOSED) {
            throw new IllegalStateException("El turno ya está CERRADO.");
        }

        // Query invoices and POS devoluciones (credit notes) for this shift
        List<SalesDocument> invoices = documentRepo.findByShiftIdAndType(
                shiftId, SalesDocumentType.INVOICE
        );
        List<SalesDocument> creditNotes = documentRepo.findByShiftIdAndType(
                shiftId, SalesDocumentType.CREDIT_NOTE
        );

        BigDecimal invoicedTotal = invoices.stream()
                .map(SalesDocument::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Credit notes store a NEGATIVE totalAmount: PosDevolutionUseCase negates every
        // returned item's quantity/subtotal/tax. Adding their sum therefore yields
        // invoiced MINUS returned, so a full devolución of the shift nets to ~0.
        BigDecimal creditNoteTotal = creditNotes.stream()
                .map(SalesDocument::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expectedTotal = invoicedTotal.add(creditNoteTotal);

        // Positive magnitude of what was returned, for display in the Z-Report.
        BigDecimal returnedTotal = creditNoteTotal.negate();

        BigDecimal actualTotal = cashCount.totalCash()
                .add(cashCount.totalCard())
                .add(cashCount.totalTransfer())
                .add(cashCount.totalCredit());

        BigDecimal difference = expectedTotal.subtract(actualTotal);

        // Generate Z-Report
        String zReport = buildZReport(existing, expectedTotal, actualTotal, difference,
                invoices.size(), creditNotes.size(), returnedTotal, cashCount, OffsetDateTime.now());

        var closed = shiftRepository.save(new Shift(
                existing.id(),
                existing.cashRegisterId(),
                existing.userId(),
                existing.openingTime(),
                OffsetDateTime.now(),
                existing.openingAmount(),
                actualTotal,
                ShiftStatus.CLOSED,
                zReport,
                existing.createdAt()
        ));

        return ShiftResponse.from(closed);
    }

    private String buildZReport(
            Shift shift,
            BigDecimal expectedTotal,
            BigDecimal actualTotal,
            BigDecimal difference,
            int invoiceCount,
            int creditNoteCount,
            BigDecimal returnedAmount,
            CashCountRequest cashCount,
            OffsetDateTime closeTime
    ) {
        var dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        var currencyFormat = "COP $%,.0f";

        var sb = new StringBuilder();
        sb.append("===========================================\n");
        sb.append("           REPORTE Z — CIERRE DE CAJA\n");
        sb.append("===========================================\n\n");

        sb.append("INFORMACIÓN DEL TURNO\n");
        sb.append("-------------------------------------------\n");
        sb.append("Turno:      #").append(extractShortId(shift.id())).append("\n");
        sb.append("Caja:       ").append(shift.cashRegisterId()).append("\n");
        sb.append("Usuario:    ").append(shift.userId()).append("\n");
        sb.append("Apertura:   ").append(shift.openingTime().format(dtf)).append("\n");
        sb.append("Cierre:     ").append(closeTime.format(dtf)).append("\n\n");

        sb.append("RESUMEN DE VENTAS\n");
        sb.append("-------------------------------------------\n");
        sb.append("Facturas:   ").append(invoiceCount).append("\n");
        sb.append("Devoluciones: ").append(creditNoteCount).append("\n");
        sb.append("Devuelto:   ").append(String.format(currencyFormat, returnedAmount)).append("\n");
        sb.append("Total esp.: ").append(String.format(currencyFormat, expectedTotal)).append("\n\n");

        sb.append("DESGLOSE DE PAGOS\n");
        sb.append("-------------------------------------------\n");
        sb.append("Efectivo:      ").append(String.format(currencyFormat, cashCount.totalCash())).append("\n");
        sb.append("Tarjeta:       ").append(String.format(currencyFormat, cashCount.totalCard())).append("\n");
        sb.append("Transferencia: ").append(String.format(currencyFormat, cashCount.totalTransfer())).append("\n");
        sb.append("Crédito:       ").append(String.format(currencyFormat, cashCount.totalCredit())).append("\n");
        sb.append("-------------------------------------------\n");
        sb.append("TOTAL CONTADO: ").append(String.format(currencyFormat, actualTotal)).append("\n\n");

        sb.append("DIFERENCIA\n");
        sb.append("-------------------------------------------\n");
        sb.append("Total esperado: ").append(String.format(currencyFormat, expectedTotal)).append("\n");
        sb.append("Total contado:  ").append(String.format(currencyFormat, actualTotal)).append("\n");
        sb.append("Diferencia:     ").append(String.format(currencyFormat, difference)).append("\n");

        if (difference.compareTo(BigDecimal.ZERO) == 0) {
            sb.append("Estado: CUADRE PERFECTO\n");
        } else if (difference.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("Estado: FALTANTE\n");
        } else {
            sb.append("Estado: SOBRANTE\n");
        }
        sb.append("\n");

        if (cashCount.notes() != null && !cashCount.notes().isBlank()) {
            sb.append("NOTAS\n");
            sb.append("-------------------------------------------\n");
            sb.append(cashCount.notes()).append("\n");
        }

        sb.append("===========================================\n");
        sb.append("  Reporte generado: ").append(closeTime.format(dtf)).append("\n");
        sb.append("===========================================\n");

        return sb.toString();
    }

    private String extractShortId(UUID id) {
        var str = id.toString();
        return str.substring(0, 8).toUpperCase();
    }
}
