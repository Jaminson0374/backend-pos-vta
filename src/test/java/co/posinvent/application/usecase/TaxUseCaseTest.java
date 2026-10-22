package co.posinvent.application.usecase;

import co.posinvent.application.dto.TaxRequest;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.Tax;
import co.posinvent.domain.repository.TaxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxUseCaseTest {

    @Mock
    private TaxRepository repository;

    private TaxUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TaxUseCase(repository);
    }

    @Test
    void createNormalizesCodeAndPersistsCatalogFields() {
        when(repository.existsByCode("IVA_19")).thenReturn(false);
        when(repository.save(any(Tax.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = useCase.create(request(" iva_19 ", "IVA 19%", "19", "01", "13156"));

        var captor = ArgumentCaptor.forClass(Tax.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().code()).isEqualTo("IVA_19");
        assertThat(captor.getValue().percentage()).isEqualByComparingTo("19");
        assertThat(captor.getValue().dianCode()).isEqualTo("01");
        assertThat(captor.getValue().siigoTaxId()).isEqualTo("13156");
        assertThat(response.code()).isEqualTo("IVA_19");
    }

    @Test
    void createRejectsDuplicateCode() {
        when(repository.existsByCode("IVA_19")).thenReturn(true);

        assertThatThrownBy(() -> useCase.create(request("IVA_19", "IVA", "19", "01", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DUPLICATE_TAX_CODE"));
        verify(repository, never()).save(any());
    }

    @Test
    void deleteSoftDisablesTheTax() {
        var id = UUID.randomUUID();
        var existing = new Tax(id, "IVA_19", "IVA 19%", new BigDecimal("19"),
                null, "01", "13156", 1, true);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(Tax.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.delete(id);

        var captor = ArgumentCaptor.forClass(Tax.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(id);
        assertThat(captor.getValue().active()).isFalse();
    }

    private TaxRequest request(String code, String description, String percentage,
                               String dianCode, String siigoTaxId) {
        return new TaxRequest(code, description, new BigDecimal(percentage),
                "obs", dianCode, siigoTaxId, 1, true);
    }
}
