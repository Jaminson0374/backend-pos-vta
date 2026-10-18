package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.DigitalCertificate;
import co.posinvent.domain.repository.DigitalCertificateRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class DigitalCertificateRepositoryAdapter implements DigitalCertificateRepository {

    private final DigitalCertificateJpaRepository jpa;
    private final DigitalCertificateMapper mapper;

    DigitalCertificateRepositoryAdapter(DigitalCertificateJpaRepository jpa, DigitalCertificateMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public DigitalCertificate save(DigitalCertificate domain) {
        var entity = domain.id() != null
                ? jpa.findById(domain.id()).orElse(new DigitalCertificateEntity())
                : new DigitalCertificateEntity();
        entity.setName(domain.name());
        entity.setIssuer(domain.issuer());
        entity.setSubject(domain.subject());
        entity.setCertificateData(domain.certificateData());
        entity.setPasswordEncrypted(domain.passwordEncrypted());
        entity.setValidUntil(domain.validUntil());
        entity.setActive(domain.active() != null ? domain.active() : true);
        var saved = jpa.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<DigitalCertificate> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<DigitalCertificate> findAll() {
        return jpa.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }
}
