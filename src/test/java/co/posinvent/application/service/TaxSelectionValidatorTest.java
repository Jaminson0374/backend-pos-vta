package co.posinvent.application.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.FiscalResponsibility;
import co.posinvent.domain.model.TaxResponsibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxSelectionValidatorTest {

    private TaxSelectionValidator validator;

    private final List<TaxResponsibility> taxCatalog = List.of(
            new TaxResponsibility(UUID.randomUUID(), "IVA_RESPONSABLE", "Responsable de IVA",
                    List.of("IVA_NO_RESPONSABLE", "IVA_INC"), 1),
            new TaxResponsibility(UUID.randomUUID(), "IVA_NO_RESPONSABLE", "No responsable de IVA",
                    List.of("IVA_RESPONSABLE", "IVA_INC"), 2),
            new TaxResponsibility(UUID.randomUUID(), "INC_RESPONSABLE", "Impuesto Nacional al Consumo (INC)",
                    List.of("INC_NO_RESPONSABLE", "IVA_INC"), 3),
            new TaxResponsibility(UUID.randomUUID(), "INC_NO_RESPONSABLE", "No responsable de INC",
                    List.of("INC_RESPONSABLE", "IVA_INC"), 4),
            new TaxResponsibility(UUID.randomUUID(), "IVA_INC", "Responsable de IVA e INC",
                    List.of("IVA_RESPONSABLE", "IVA_NO_RESPONSABLE", "INC_RESPONSABLE", "INC_NO_RESPONSABLE"), 5),
            new TaxResponsibility(UUID.randomUUID(), "REGIMEN_ESPECIAL", "Régimen especial",
                    List.of(), 6)
    );

    private final List<FiscalResponsibility> fiscalCatalog = List.of(
            new FiscalResponsibility(UUID.randomUUID(), "GRAN_CONTRIBUYENTE", "Gran contribuyente",
                    List.of("REGIMEN_SIMPLE", "NO_APLICA"), 1),
            new FiscalResponsibility(UUID.randomUUID(), "REGIMEN_SIMPLE", "Régimen simple de tributación",
                    List.of("GRAN_CONTRIBUYENTE", "NO_APLICA"), 2),
            new FiscalResponsibility(UUID.randomUUID(), "AUTORRETENEDOR", "Autorretenedor",
                    List.of("NO_APLICA"), 3),
            new FiscalResponsibility(UUID.randomUUID(), "AGENTE_RETENCION_IVA", "Agente de retención IVA",
                    List.of("NO_APLICA"), 4),
            new FiscalResponsibility(UUID.randomUUID(), "NO_APLICA", "No aplica",
                    List.of("GRAN_CONTRIBUYENTE", "REGIMEN_SIMPLE", "AUTORRETENEDOR", "AGENTE_RETENCION_IVA"), 5)
    );

    @BeforeEach
    void setUp() {
        validator = new TaxSelectionValidator();
    }

    @Test
    void shouldAllowEmptyTaxResponsibilitySelection() {
        assertThatCode(() -> validator.validateTaxResponsibilities(taxCatalog, List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateTaxResponsibilities(taxCatalog, null))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowEmptyFiscalResponsibilitySelection() {
        assertThatCode(() -> validator.validateFiscalResponsibilities(fiscalCatalog, List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateFiscalResponsibilities(fiscalCatalog, null))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowNonConflictingTaxResponsibilities() {
        assertThatCode(() -> validator.validateTaxResponsibilities(
                taxCatalog, List.of("IVA_RESPONSABLE", "INC_RESPONSABLE")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectConflictingTaxResponsibilities() {
        assertThatThrownBy(() -> validator.validateTaxResponsibilities(
                taxCatalog, List.of("IVA_RESPONSABLE", "IVA_NO_RESPONSABLE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_TAX_SELECTION");
    }

    @Test
    void shouldRejectCombinedWithIndividualTaxResponsibility() {
        assertThatThrownBy(() -> validator.validateTaxResponsibilities(
                taxCatalog, List.of("IVA_INC", "IVA_RESPONSABLE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_TAX_SELECTION");
    }

    @Test
    void shouldRejectNoAplicaWithGranContribuyente() {
        assertThatThrownBy(() -> validator.validateFiscalResponsibilities(
                fiscalCatalog, List.of("NO_APLICA", "GRAN_CONTRIBUYENTE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_TAX_SELECTION");
    }

    @Test
    void shouldRejectGranContribuyenteWithRegimenSimple() {
        assertThatThrownBy(() -> validator.validateFiscalResponsibilities(
                fiscalCatalog, List.of("GRAN_CONTRIBUYENTE", "REGIMEN_SIMPLE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_TAX_SELECTION");
    }

    @Test
    void shouldRejectUnknownCode() {
        assertThatThrownBy(() -> validator.validateTaxResponsibilities(
                taxCatalog, List.of("UNKNOWN_CODE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_TAX_SELECTION");
    }
}
