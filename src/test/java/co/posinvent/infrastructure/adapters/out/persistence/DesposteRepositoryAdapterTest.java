package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Desposte;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesposteRepositoryAdapterTest {

    @Mock
    private DesposteJpaRepository jpa;

    @Mock
    private DesposteMapper mapper;

    private DesposteRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DesposteRepositoryAdapter(jpa, mapper);
    }

    @Test
    void findPage_withBothBounds_usesClosedRange() {
        var from = LocalDate.of(2026, 5, 1);
        var to = LocalDate.of(2026, 5, 31);
        when(jpa.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        adapter.findPage(from, to, 0, 20);

        verify(jpa).findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(from.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime()),
                eq(to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime()),
                any(Pageable.class));
    }

    @Test
    void findPage_withFromOnly_usesLowerBound() {
        var from = LocalDate.of(2026, 5, 1);
        when(jpa.findByCreatedAtGreaterThanEqual(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        adapter.findPage(from, null, 0, 20);

        verify(jpa).findByCreatedAtGreaterThanEqual(
                eq(from.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime()),
                any(Pageable.class));
    }

    @Test
    void findPage_withToOnly_usesExclusiveUpperBound() {
        var to = LocalDate.of(2026, 5, 31);
        when(jpa.findByCreatedAtLessThan(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        adapter.findPage(null, to, 0, 20);

        verify(jpa).findByCreatedAtLessThan(
                eq(to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime()),
                any(Pageable.class));
    }

    @Test
    void findPage_withoutBounds_findAll() {
        when(jpa.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        adapter.findPage(null, null, 0, 20);

        verify(jpa).findAll(any(Pageable.class));
    }

    @Test
    void save_newDesposte_attachesCutsBeforePersisting() {
        var domain = desposte(null);
        var entity = new DesposteEntity();
        when(mapper.toEntity(domain)).thenReturn(entity);
        when(mapper.toCutEntity(any(Desposte.DesposteCut.class))).thenAnswer(inv -> new DesposteCutEntity());
        when(jpa.save(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(domain);

        adapter.save(domain);

        assertThat(entity.getCuts()).hasSize(1);
        assertThat(entity.getCuts().get(0).getDesposte()).isSameAs(entity);
        verify(jpa).save(entity);
    }

    @Test
    void save_unknownId_throwsNotFound() {
        var id = UUID.randomUUID();
        var domain = desposte(id);
        when(jpa.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.save(domain))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Desposte desposte(UUID id) {
        var cut = new Desposte.DesposteCut(
                null,
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
