package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.EmployeeBasicData;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ThirdPartyRepositoryAdapter implements ThirdPartyRepository {

    private final ThirdPartyJpaRepository jpa;
    private final EmployeeDetailsJpaRepository employeeDetailsJpa;
    private final ThirdPartyMapper mapper;

    ThirdPartyRepositoryAdapter(ThirdPartyJpaRepository jpa,
                                EmployeeDetailsJpaRepository employeeDetailsJpa,
                                ThirdPartyMapper mapper) {
        this.jpa = jpa;
        this.employeeDetailsJpa = employeeDetailsJpa;
        this.mapper = mapper;
    }

    @Override
    public ThirdParty save(ThirdParty thirdParty) {
        ThirdPartyEntity entity;

        if (thirdParty.id() != null) {
            entity = jpa.findById(thirdParty.id())
                    .orElseThrow(() -> new ResourceNotFoundException("Tercero", thirdParty.id()));
            mapper.updateEntity(entity, thirdParty);
        } else {
            entity = mapper.toEntity(thirdParty);
        }

        ThirdPartyEntity saved;
        try {
            saved = jpa.save(entity);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new BusinessException("CONCURRENT_MODIFICATION",
                    "Este tercero fue modificado por otro usuario. Recargá los datos antes de guardar.");
        }

        saveEmployeeDetails(saved, thirdParty.employeeData());

        return mapper.toDomain(saved);
    }

    private void saveEmployeeDetails(ThirdPartyEntity saved, EmployeeBasicData data) {
        if (data != null) {
            // Never touch saved.getEmployeeDetails() — avoid lazy-proxy issues.
            // Load directly by PK; if absent, create a brand-new entity WITHOUT
            // setting the id manually so Spring Data calls em.persist(), letting
            // @MapsId derive the id from the thirdParty association automatically.
            EmployeeDetailsEntity emp = employeeDetailsJpa.findById(saved.getId())
                    .orElseGet(() -> {
                        EmployeeDetailsEntity e = new EmployeeDetailsEntity();
                        e.setThirdParty(saved);
                        return e;
                    });
            emp.setPosition(data.position());
            emp.setCostCenter(data.costCenter());
            emp.setWorkCenter(data.workCenter());
            emp.setGender(data.gender());
            emp.setCivilStatus(data.civilStatus());
            emp.setSalesGroup(data.salesGroup());
            emp.setBirthDate(data.birthDate());
            emp.setBirthPlace(data.birthPlace());
            emp.setMilitaryId(data.militaryId());
            emp.setForeigner(data.isForeigner());
            emp.setNatResidentExterior(data.natResidentExterior());
            emp.setDeclarant(data.isDeclarant());
            emp.setAssociatedSeller(data.associatedSeller());
            emp.setRequiresEndowment(data.requiresEndowment());
            emp.setSenior(data.isSenior());
            EmployeeDetailsEntity savedEmp = employeeDetailsJpa.save(emp);
            saved.setEmployeeDetails(savedEmp);
        } else {
            employeeDetailsJpa.findById(saved.getId()).ifPresent(employeeDetailsJpa::delete);
            saved.setEmployeeDetails(null);
        }
    }

    @Override
    public Optional<ThirdParty> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Page<ThirdParty> findAll(Pageable pageable) {
        return jpa.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public List<ThirdParty> findSuppliers() {
        return jpa.findSuppliers(
                        List.of(ThirdParty.ThirdPartyType.SUPPLIER, ThirdParty.ThirdPartyType.BOTH),
                        List.of("SUPPLIER", "BOTH")
                )
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Page<ThirdParty> search(String query, Pageable pageable) {
        return jpa.search(query, pageable).map(mapper::toDomain);
    }

    @Override
    public boolean existsByNumIdentification(String numIdentification) {
        return jpa.existsByNumIdentification(numIdentification);
    }

    @Override
    public boolean existsByNumIdentificationAndIdNot(String numIdentification, UUID id) {
        return jpa.existsByNumIdentificationAndIdNot(numIdentification, id);
    }

    @Override
    public Page<ThirdParty> findByTypeAndActive(ThirdParty.ThirdPartyType type, boolean active, Pageable pageable) {
        return jpa.findByTypeAndActive(type, active, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<ThirdParty> findByPersonTypeAndActive(ThirdParty.PersonType personType, boolean active, Pageable pageable) {
        return jpa.findByPersonTypeAndActive(personType, active, pageable).map(mapper::toDomain);
    }

    @Override
    public List<ThirdParty> findEmployeesWithoutUser() {
        return jpa.findEmployeesWithoutUser().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ThirdParty> findByNumIdentification(String numIdentification) {
        return jpa.findByNumIdentification(numIdentification).map(mapper::toDomain);
    }
}
