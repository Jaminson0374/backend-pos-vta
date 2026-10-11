package co.posinvent.application.usecase;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.DianResolution;
import co.posinvent.domain.repository.DianResolutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DianNumberingServiceTest {

    @Mock
    private DianResolutionRepository repository;

    private DianNumberingService service;

    private static final LocalDate TODAY = LocalDate.now();

    @BeforeEach
    void setUp() {
        service = new DianNumberingService(repository);
    }

    private static DianResolution resolution(Long currentNumber, long rangeFrom, long rangeTo) {
        return new DianResolution(
                UUID.randomUUID(),
                "RES-001",
                TODAY.minusDays(30),
                TODAY.minusDays(1),
                TODAY.plusDays(1),
                "FE",
                rangeFrom,
                rangeTo,
                currentNumber,
                "PIN",
                true,
                null
        );
    }

    @Test
    void nextInvoiceNumber_throwsWhenNoActiveResolution() {
        when(repository.findActiveForUpdate()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.nextInvoiceNumber())
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DIAN_NO_ACTIVE_RESOLUTION"));

        verify(repository, never()).save(any());
    }

    @Test
    void nextInvoiceNumber_throwsWhenResolutionNotValidToday() {
        var expired = new DianResolution(
                UUID.randomUUID(), "RES-001", TODAY.minusDays(60),
                TODAY.minusDays(30), TODAY.minusDays(10),
                "FE", 1L, 1000L, null, "PIN", true, null);
        when(repository.findActiveForUpdate()).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.nextInvoiceNumber())
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DIAN_RESOLUTION_EXPIRED"));

        verify(repository, never()).save(any());
    }

    @Test
    void nextInvoiceNumber_startsAtRangeFromWithPrefixAndZeroPad() {
        when(repository.findActiveForUpdate()).thenReturn(Optional.of(resolution(null, 1, 1000)));
        when(repository.save(any(DianResolution.class))).thenAnswer(inv -> inv.getArgument(0));

        var number = service.nextInvoiceNumber();

        assertThat(number).isEqualTo("FE0001");

        var captor = ArgumentCaptor.forClass(DianResolution.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().currentNumber()).isEqualTo(1L);
    }

    @Test
    void nextInvoiceNumber_incrementsAndPersistsCurrentNumber() {
        when(repository.findActiveForUpdate()).thenReturn(Optional.of(resolution(6L, 1, 1000)));
        when(repository.save(any(DianResolution.class))).thenAnswer(inv -> inv.getArgument(0));

        var number = service.nextInvoiceNumber();

        assertThat(number).isEqualTo("FE0007");

        var captor = ArgumentCaptor.forClass(DianResolution.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().currentNumber()).isEqualTo(7L);
    }

    @Test
    void nextInvoiceNumber_throwsWhenRangeExhausted() {
        when(repository.findActiveForUpdate()).thenReturn(Optional.of(resolution(1000L, 1, 1000)));

        assertThatThrownBy(() -> service.nextInvoiceNumber())
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DIAN_RANGE_EXHAUSTED"));

        verify(repository, never()).save(any());
    }

    @Test
    void nextInvoiceNumber_normalizesNullPrefix() {
        var noPrefix = new DianResolution(
                UUID.randomUUID(), "RES-001", TODAY.minusDays(30),
                TODAY.minusDays(1), TODAY.plusDays(1),
                null, 1L, 1000L, null, "PIN", true, null);
        when(repository.findActiveForUpdate()).thenReturn(Optional.of(noPrefix));
        when(repository.save(any(DianResolution.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.nextInvoiceNumber()).isEqualTo("0001");
    }
}
