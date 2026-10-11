package co.posinvent.application.usecase;

import co.posinvent.application.dto.DianResolutionRequest;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.DianResolution;
import co.posinvent.domain.repository.DianResolutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManageDianResolutionUseCaseTest {

    @Mock
    private DianResolutionRepository repository;

    private ManageDianResolutionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ManageDianResolutionUseCase(repository);
    }

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private static DianResolutionRequest request(String prefix, LocalDate validFrom, LocalDate validTo,
                                                 long rangeFrom, long rangeTo) {
        return new DianResolutionRequest(
                "RES-001",
                d(2026, 1, 1),
                validFrom,
                validTo,
                prefix,
                rangeFrom,
                rangeTo,
                "PIN",
                false
        );
    }

    private static DianResolution existing(UUID id, String prefix, LocalDate validFrom, LocalDate validTo,
                                           long rangeFrom, long rangeTo, boolean active) {
        return new DianResolution(id, "RES-EXIST", d(2026, 1, 1), validFrom, validTo,
                prefix, rangeFrom, rangeTo, "PIN", active, null);
    }

    private void echoSave() {
        doAnswer(invocation -> invocation.getArgument(0, DianResolution.class))
                .when(repository)
                .save(any(DianResolution.class));
    }

    @Test
    void create_throwsRangeInvalidWhenRangeFromGreaterThanRangeTo() {
        var req = request("F", d(2026, 1, 1), d(2026, 12, 31), 100, 50);

        assertThatThrownBy(() -> useCase.create(req))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DIAN_RANGE_INVALID"));

        verify(repository, never()).save(any());
    }

    @Test
    void create_throwsDateRangeWhenValidFromAfterValidTo() {
        var req = request("F", d(2026, 12, 31), d(2026, 1, 1), 1, 100);

        assertThatThrownBy(() -> useCase.create(req))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DIAN_DATE_RANGE"));

        verify(repository, never()).save(any());
    }

    @Test
    void create_savesResolutionWhenNoOverlap() {
        when(repository.findAll()).thenReturn(List.of());
        echoSave();

        var result = useCase.create(request("F", d(2026, 1, 1), d(2026, 12, 31), 1, 100));

        assertThat(result.resolutionNumber()).isEqualTo("RES-001");
        assertThat(result.prefix()).isEqualTo("F");
        assertThat(result.validFrom()).isEqualTo(d(2026, 1, 1));
        assertThat(result.validTo()).isEqualTo(d(2026, 12, 31));
        assertThat(result.rangeFrom()).isEqualTo(1L);
        assertThat(result.rangeTo()).isEqualTo(100L);
        assertThat(result.active()).isFalse();
        verify(repository).save(any(DianResolution.class));
    }

    @Test
    void create_throwsOverlapWhenSamePrefixAndRangesIntersect() {
        var other = existing(UUID.randomUUID(), "F", d(2026, 1, 1), d(2026, 12, 31), 1, 100, true);
        when(repository.findAll()).thenReturn(List.of(other));

        var req = request("F", d(2026, 6, 1), d(2026, 6, 30), 50, 150);

        assertThatThrownBy(() -> useCase.create(req))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DIAN_RESOLUTION_OVERLAP"));

        verify(repository, never()).save(any());
    }

    @Test
    void create_savesWhenPrefixDiffers() {
        var other = existing(UUID.randomUUID(), "A", d(2026, 1, 1), d(2026, 12, 31), 1, 100, true);
        when(repository.findAll()).thenReturn(List.of(other));
        echoSave();

        var result = useCase.create(request("B", d(2026, 6, 1), d(2026, 6, 30), 50, 150));

        assertThat(result.prefix()).isEqualTo("B");
        verify(repository).save(any(DianResolution.class));
    }

    @Test
    void create_savesWhenNumericRangesDoNotIntersect() {
        var other = existing(UUID.randomUUID(), "F", d(2026, 1, 1), d(2026, 12, 31), 1, 100, true);
        when(repository.findAll()).thenReturn(List.of(other));
        echoSave();

        var result = useCase.create(request("F", d(2026, 6, 1), d(2026, 6, 30), 200, 300));

        assertThat(result.rangeFrom()).isEqualTo(200L);
        verify(repository).save(any(DianResolution.class));
    }

    @Test
    void create_savesWhenDateRangesDoNotIntersect() {
        var other = existing(UUID.randomUUID(), "F", d(2026, 1, 1), d(2026, 12, 31), 1, 100, true);
        when(repository.findAll()).thenReturn(List.of(other));
        echoSave();

        var result = useCase.create(request("F", d(2027, 1, 1), d(2027, 12, 31), 50, 150));

        assertThat(result.validFrom()).isEqualTo(d(2027, 1, 1));
        verify(repository).save(any(DianResolution.class));
    }

    @Test
    void update_excludesItselfFromOverlapCheck() {
        var id = UUID.randomUUID();
        var self = existing(id, "F", d(2026, 1, 1), d(2026, 12, 31), 1, 100, false);
        when(repository.findById(id)).thenReturn(Optional.of(self));
        when(repository.findAll()).thenReturn(List.of(self));
        echoSave();

        var result = useCase.update(id, request("F", d(2026, 1, 1), d(2026, 12, 31), 1, 100));

        assertThat(result.id()).isEqualTo(id);
        verify(repository).save(any(DianResolution.class));
    }

    @Test
    void update_throwsResourceNotFoundWhenIdMissing() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(id, request("F", d(2026, 1, 1), d(2026, 12, 31), 1, 100)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void activate_deactivatesOthersThenActivatesTarget() {
        var id = UUID.randomUUID();
        var target = existing(id, "F", d(2026, 1, 1), d(2026, 12, 31), 1, 100, false);
        when(repository.findById(id)).thenReturn(Optional.of(target));
        echoSave();

        var result = useCase.activate(id);

        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).deactivateAll();
        inOrder.verify(repository).save(any(DianResolution.class));

        var captor = ArgumentCaptor.forClass(DianResolution.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(id);
        assertThat(captor.getValue().active()).isTrue();
        assertThat(result.active()).isTrue();
    }

    @Test
    void deactivate_setsActiveFalse() {
        var id = UUID.randomUUID();
        var target = existing(id, "F", d(2026, 1, 1), d(2026, 12, 31), 1, 100, true);
        when(repository.findById(id)).thenReturn(Optional.of(target));
        echoSave();

        var result = useCase.deactivate(id);

        var captor = ArgumentCaptor.forClass(DianResolution.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(id);
        assertThat(captor.getValue().active()).isFalse();
        assertThat(result.active()).isFalse();
        verify(repository, never()).deactivateAll();
    }

    @Test
    void getById_throwsWhenNotFound() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_throwsWhenNotFound() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).deleteById(any());
    }
}
