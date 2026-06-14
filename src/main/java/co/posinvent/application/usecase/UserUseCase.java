package co.posinvent.application.usecase;

import co.posinvent.application.annotation.Auditable;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.UserRequest;
import co.posinvent.application.dto.UserResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Role;
import co.posinvent.domain.model.User;
import co.posinvent.domain.repository.UserRepository;
import co.posinvent.infrastructure.adapters.out.persistence.RoleJpaRepository;
import co.posinvent.infrastructure.adapters.out.persistence.RoleMapper;
import co.posinvent.infrastructure.adapters.out.persistence.UserEntity;
import co.posinvent.infrastructure.adapters.out.persistence.UserJpaRepository;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class UserUseCase {

    private final UserRepository userRepository;
    private final UserJpaRepository userJpaRepository;
    private final RoleJpaRepository roleJpaRepository;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public UserUseCase(
            UserRepository userRepository,
            UserJpaRepository userJpaRepository,
            RoleJpaRepository roleJpaRepository,
            RoleMapper roleMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.userJpaRepository = userJpaRepository;
        this.roleJpaRepository = roleJpaRepository;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Auditable(entityType = "USER", action = "CREATE")
    @Transactional
    public UserResponse create(UserRequest request) {
        // Validar role
        var roleEntity = roleJpaRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Rol", request.roleId()));

        // Generar password temporal
        var tempPassword = generateTempPassword();

        // Crear entidad
        var now = OffsetDateTime.now();
        var entity = new UserEntity();
        entity.setUsername(request.username());
        entity.setFullName(request.fullName());
        entity.setEmail(request.email());
        entity.setRole(roleEntity);
        entity.setActive(request.isActive());
        entity.setPasswordHash(passwordEncoder.encode(tempPassword));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        var saved = userJpaRepository.save(entity);
        // Cargar con relaciones para mapeo completo
        var reloaded = userJpaRepository.findById(saved.getId()).orElseThrow();
        var domain = toDomain(reloaded);

        return UserResponse.withTempPassword(domain, tempPassword);
    }

    @Auditable(entityType = "USER", action = "UPDATE")
    @Transactional
    public UserResponse update(UUID id, UserRequest request) {
        var entity = userJpaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));

        var newRole = roleJpaRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Rol", request.roleId()));

        // Validar no auto-desactivación
        var currentUser = getCurrentUserId();
        if (entity.getId().equals(currentUser) && !request.isActive()) {
            throw new BusinessException(
                    "SELF_DEACTIVATION",
                    "No puedes desactivar tu propio usuario."
            );
        }

        // Validar no quitar ADMIN del último admin activo
        var currentRoleName = entity.getRole().getName();
        if ("ADMIN".equals(currentRoleName) && !"ADMIN".equals(newRole.getName())) {
            long adminCount = userJpaRepository.countByRoleNameAndActive("ADMIN", true);
            if (adminCount <= 1) {
                throw new BusinessException(
                        "LAST_ADMIN",
                        "No se puede cambiar el rol del último administrador activo."
                );
            }
        }

        // Si se está desactivando al último admin
        if (entity.getId().equals(currentUser) && "ADMIN".equals(currentRoleName) && !request.isActive()) {
            long adminCount = userJpaRepository.countByRoleNameAndActive("ADMIN", true);
            if (adminCount <= 1) {
                throw new BusinessException(
                        "LAST_ADMIN",
                        "No se puede desactivar al último administrador activo."
                );
            }
        }

        entity.setUsername(request.username());
        entity.setFullName(request.fullName());
        entity.setEmail(request.email());
        entity.setRole(newRole);
        entity.setActive(request.isActive());
        entity.setUpdatedAt(OffsetDateTime.now());

        var saved = userJpaRepository.save(entity);
        var reloaded = userJpaRepository.findById(saved.getId()).orElseThrow();

        return UserResponse.from(toDomain(reloaded));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(int page, int size, String search, String role, Boolean active) {
        var pageable = PageRequest.of(page, size, Sort.by("fullName").ascending());
        var searchPattern = search != null && !search.isBlank() ? "%" + search.toLowerCase() + "%" : null;
        var userPage = userRepository.findFiltered(
                searchPattern,
                role != null && !role.isBlank() ? role : null,
                active,
                pageable
        );
        return PageResponse.from(userPage, UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
    }

    private UUID getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PosUserDetails pud) {
            return pud.userId();
        }
        return null;
    }

    private String generateTempPassword() {
        var sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private User toDomain(UserEntity entity) {
        var role = entity.getRole() != null ? roleMapper.toDomain(entity.getRole()) : null;
        return new User(
                entity.getId(),
                entity.getUsername(),
                entity.getFullName(),
                entity.getEmail(),
                role,
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
