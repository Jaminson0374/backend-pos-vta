package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.DianDashboardSummary;
import co.posinvent.domain.model.ElectronicInvoiceStatus;
import co.posinvent.domain.repository.ElectronicInvoiceRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
public class DashboardDianController {

    private final ElectronicInvoiceRepository electronicInvoiceRepository;

    public DashboardDianController(ElectronicInvoiceRepository electronicInvoiceRepository) {
        this.electronicInvoiceRepository = electronicInvoiceRepository;
    }

    @GetMapping("/dian-summary")
    public DianDashboardSummary getDianSummary() {
        var todayEmitted = electronicInvoiceRepository.countIssuedToday();
        var pendingCount = electronicInvoiceRepository.countByStatus(ElectronicInvoiceStatus.PENDING_SEND)
                + electronicInvoiceRepository.countByStatus(ElectronicInvoiceStatus.SENT);
        var rejectedCount = electronicInvoiceRepository.countByStatus(ElectronicInvoiceStatus.REJECTED_BY_DIAN);

        return new DianDashboardSummary(todayEmitted, pendingCount, rejectedCount);
    }
}
