package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.RoleResponse;
import co.posinvent.infrastructure.adapters.out.persistence.RoleJpaRepository;
import co.posinvent.infrastructure.adapters.out.persistence.RoleMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
