package co.posinvent.application.service;

import co.posinvent.infrastructure.adapters.out.persistence.JwtBlacklistEntity;
import co.posinvent.infrastructure.adapters.out.persistence.JwtBlacklistJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtBlacklistServiceTest {

    @Mock
    private JwtBlacklistJpaRepository repository;

    @InjectMocks
    private JwtBlacklistService service;

    @Test
    void revokeInsertsRowWithJtiExpiryAndRevocationTime() {
        var jti = UUID.randomUUID();
        var expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2);
        when(repository.findById(jti)).thenReturn(Optional.empty());

        service.revoke(jti, expiresAt);

        var captor = ArgumentCaptor.forClass(JwtBlacklistEntity.class);
        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getJti()).isEqualTo(jti);
        assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(saved.getRevokedAt()).isNotNull();
    }

    @Test
    void revokeUpdatesExistingRowWithoutDuplicatingIt() {
        var jti = UUID.randomUUID();
        var previousExpiry = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
        var newExpiry = OffsetDateTime.now(ZoneOffset.UTC).plusHours(3);
        var existing = new JwtBlacklistEntity();
        existing.setJti(jti);
        existing.setExpiresAt(previousExpiry);
        existing.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        when(repository.findById(jti)).thenReturn(Optional.of(existing));

        service.revoke(jti, newExpiry);

        var captor = ArgumentCaptor.forClass(JwtBlacklistEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(newExpiry);
    }

    @Test
    void isRevokedDelegatesToRepositoryExists() {
        var jti = UUID.randomUUID();
        when(repository.existsById(jti)).thenReturn(true);

        assertThat(service.isRevoked(jti)).isTrue();

        verify(repository).existsById(jti);
    }

    @Test
    void isRevokedReturnsFalseForUnknownJti() {
        var jti = UUID.randomUUID();
        when(repository.existsById(jti)).thenReturn(false);

        assertThat(service.isRevoked(jti)).isFalse();
    }

    @Test
    void purgeExpiredDeletesRowsBeforeNow() {
        var before = OffsetDateTime.now(ZoneOffset.UTC);

        service.purgeExpired();

        var captor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteByExpiresAtBefore(captor.capture());
        assertThat(captor.getValue()).isAfterOrEqualTo(before).isBeforeOrEqualTo(OffsetDateTime.now(ZoneOffset.UTC));
        verify(repository, never()).save(any());
    }
}
