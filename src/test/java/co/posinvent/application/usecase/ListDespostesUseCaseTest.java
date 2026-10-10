package co.posinvent.application.usecase;

import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Desposte;
import co.posinvent.domain.repository.DesposteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListDespostesUseCaseTest {

    @Mock
    private DesposteRepository desposteRepository;

    private ListDespostesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListDespostesUseCase(desposteRepository);
    }

    @Test
    void findPage_mapsRepositoryPageToResponse() {
        var from = LocalDate.of(2026, 5, 1);
        var to = LocalDate.of(2026, 5, 31);
        var desposte = desposte(UUID.randomUUID());
        when(desposteRepository.findPage(from, to, 0, 20))
                .thenReturn(new PageImpl<>(List.of(desposte)));

        var result = useCase.findPage(from, to, 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(desposte.id());
        assertThat(result.content().get(0).sourceBatchId()).isEqualTo(desposte.sourceBatchId());
        assertThat(result.content().get(0).yieldPercentage()).isEqualByComparingTo("95.0000");
        assertThat(result.content().get(0).cuts()).hasSize(1);
    }

    @Test
    void findById_returnsMappedResponse() {
        var id = UUID.randomUUID();
        var desposte = desposte(id);
        when(desposteRepository.findById(id)).thenReturn(Optional.of(desposte));

        var result = useCase.findById(id);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.withinTolerance()).isTrue();
        assertThat(result.cuts()).hasSize(1);
        assertThat(result.cuts().get(0).childBatchId()).isEqualTo(desposte.cuts().get(0).childBatchId());
    }

    @Test
    void findById_throwsWhenMissing() {
        var id = UUID.randomUUID();
        when(desposteRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Desposte desposte(UUID id) {
        var cut = new Desposte.DesposteCut(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("95.000000"),
                new BigDecimal("20.000000"),
                new BigDecimal("1900.000000"),
                new BigDecimal("1000.000000"),
                new BigDecimal("10.526316"),
                null);
        return new Desposte(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.000000"),
                new BigDecimal("95.000000"),
                new BigDecimal("4.000000"),
                new BigDecimal("0.500000"),
                new BigDecimal("0.500000"),
                new BigDecimal("0.500000"),
                true,
                new BigDecimal("95.0000"),
                new BigDecimal("1900.000000"),
                new BigDecimal("1000.000000"),
                "nota",
                UUID.randomUUID().toString(),
                OffsetDateTime.now(),
                List.of(cut));
    }
}
