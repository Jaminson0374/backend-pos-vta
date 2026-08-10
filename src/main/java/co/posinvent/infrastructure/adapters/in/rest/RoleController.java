package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.RoleResponse;
import co.posinvent.application.dto.RoleUpdateRequest;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.infrastructure.adapters.out.persistence.RoleJpaRepository;
import co.posinvent.infrastructure.adapters.out.persistence.RoleMapper;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/roles")
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {

    private final RoleJpaRepository roleJpaRepository;
    private final RoleMapper roleMapper;

    public RoleController(RoleJpaRepository roleJpaRepository, RoleMapper roleMapper) {
        this.roleJpaRepository = roleJpaRepository;
        this.roleMapper = roleMapper;
    }

    @GetMapping
    public List<RoleResponse> listAll() {
        return roleJpaRepository.findAll().stream()
                .map(roleMapper::toDomain)
                .map(RoleResponse::from)
                .toList();
    }

    @PutMapping("/{id}")
    public RoleResponse update(@PathVariable UUID id, @Valid @RequestBody RoleUpdateRequest request) {
        var entity = roleJpaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rol", id));
        entity.setPermissions(request.permissions());
        var saved = roleJpaRepository.save(entity);
        return RoleResponse.from(roleMapper.toDomain(saved));
    }
}
