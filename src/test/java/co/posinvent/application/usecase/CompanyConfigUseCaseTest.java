package co.posinvent.application.usecase;

import co.posinvent.application.dto.CompanyConfigRequest;
import co.posinvent.application.service.TaxSelectionValidator;
import co.posinvent.domain.model.CompanyConfig;
import co.posinvent.domain.repository.CompanyConfigRepository;
import co.posinvent.domain.repository.FiscalResponsibilityRepository;
import co.posinvent.domain.repository.IdentificationTypeRepository;
import co.posinvent.domain.repository.TaxResponsibilityRepository;
import co.posinvent.domain.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the discount ceilings in {@code company_config}: an admin
 * save must never silently reset {@code max_discount_pct}/{@code cashier_max_discount_pct}
 * back to their defaults when the request does not carry them.
 */
@ExtendWith(MockitoExtension.class)
class CompanyConfigUseCaseTest {

    @Mock private CompanyConfigRepository repository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private IdentificationTypeRepository identificationTypeRepository;
    @Mock private TaxResponsibilityRepository taxResponsibilityRepository;
    @Mock private FiscalResponsibilityRepository fiscalResponsibilityRepository;
    @Mock private TaxSelectionValidator validator;

    private CompanyConfigUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CompanyConfigUseCase(
                repository, warehouseRepository, identificationTypeRepository,
                taxResponsibilityRepository, fiscalResponsibilityRepository, validator);

        when(taxResponsibilityRepository.findAllActive()).thenReturn(List.of());
        when(fiscalResponsibilityRepository.findAllActive()).thenReturn(List.of());
    }

    @Test
    void saveConfigKeepsExistingDiscountCeilingsWhenRequestOmitsThem() {
        // Existing persisted ceilings differ from the defaults (30 / 5).
        when(repository.findConfig()).thenReturn(Optional.of(config("15", "3")));
        when(repository.save(any(CompanyConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.saveConfig(request(null, null));

        var saved = captureSaved();
        assertThat(saved.maxDiscountPct()).isEqualByComparingTo("15");
        assertThat(saved.cashierMaxDiscountPct()).isEqualByComparingTo("3");
    }

    @Test
    void saveConfigPersistsDiscountCeilingsFromTheRequest() {
        when(repository.findConfig()).thenReturn(Optional.of(config("15", "3")));
        when(repository.save(any(CompanyConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.saveConfig(request("45", "8"));

        var saved = captureSaved();
        assertThat(saved.maxDiscountPct()).isEqualByComparingTo("45");
        assertThat(saved.cashierMaxDiscountPct()).isEqualByComparingTo("8");
    }

    private CompanyConfig captureSaved() {
        var captor = ArgumentCaptor.forClass(CompanyConfig.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private CompanyConfig config(String maxDiscountPct, String cashierMaxDiscountPct) {
        return new CompanyConfig(
                1L, "Company", "NIT", null, null, null, null, null, null, false,
                List.of(), List.of(), List.of(), null, "COP", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, true, null, null, null, null,
                new BigDecimal(maxDiscountPct), new BigDecimal(cashierMaxDiscountPct));
    }

    private CompanyConfigRequest request(String maxDiscountPct, String cashierMaxDiscountPct) {
        return new CompanyConfigRequest(
                "Company", "NIT", null, null, null, null, null, null, false,
                List.of(), List.of(), List.of(), null, "COP", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, true,
                maxDiscountPct == null ? null : new BigDecimal(maxDiscountPct),
                cashierMaxDiscountPct == null ? null : new BigDecimal(cashierMaxDiscountPct),
                null, null, null, null, null, null, null, null, null, null);
    }
}
