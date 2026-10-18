package co.posinvent.application.service;

import co.posinvent.infrastructure.adapters.out.persistence.JwtBlacklistJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtBlacklistServiceTest {

    @Mock
    private JwtBlacklistJpaRepository repository;

    @InjectMocks
    private JwtBlacklistService service;

    @Test
    void revokeUpsertsRowWithJtiAndExpiry() {
        var jti = UUID.randomUUID();
        var expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2);

        service.revoke(jti, expiresAt);

        verify(repository).upsert(jti, expiresAt);
        verify(repository, never()).save(any());
        verify(repository, never()).findById(any());
    }

    @Test
    void revokeIsIdempotentForDuplicateJti() {
        var jti = UUID.randomUUID();
        var expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(2);

        service.revoke(jti, expiresAt);
        service.revoke(jti, expiresAt);

        verify(repository, times(2)).upsert(jti, expiresAt);
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
        when(repository.deleteExpiredBefore(any())).thenReturn(7);

        service.purgeExpired();

        var captor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteExpiredBefore(captor.capture());
        assertThat(captor.getValue()).isAfterOrEqualTo(before).isBeforeOrEqualTo(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
