package co.posinvent.application.usecase;

import co.posinvent.application.annotation.Auditable;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.UserRequest;
import co.posinvent.application.dto.UserResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Role;
import co.posinvent.domain.model.ThirdParty.ThirdPartyType;
import co.posinvent.domain.model.User;
import co.posinvent.domain.repository.UserRepository;
import co.posinvent.infrastructure.adapters.out.persistence.RoleJpaRepository;
import co.posinvent.infrastructure.adapters.out.persistence.RoleMapper;
import co.posinvent.infrastructure.adapters.out.persistence.ThirdPartyJpaRepository;
import co.posinvent.infrastructure.adapters.out.persistence.UserEntity;
import co.posinvent.infrastructure.adapters.out.persistence.UserJpaRepository;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final ThirdPartyJpaRepository thirdPartyJpaRepository;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetUseCase passwordResetUseCase;

    private static final Logger log = LoggerFactory.getLogger(UserUseCase.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public UserUseCase(
            UserRepository userRepository,
            UserJpaRepository userJpaRepository,
            RoleJpaRepository roleJpaRepository,
            ThirdPartyJpaRepository thirdPartyJpaRepository,
            RoleMapper roleMapper,
            PasswordEncoder passwordEncoder,
            PasswordResetUseCase passwordResetUseCase
    ) {
        this.userRepository = userRepository;
        this.userJpaRepository = userJpaRepository;
        this.roleJpaRepository = roleJpaRepository;
        this.thirdPartyJpaRepository = thirdPartyJpaRepository;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetUseCase = passwordResetUseCase;
    }

    @Auditable(entityType = "USER", action = "CREATE")
    @Transactional
    public UserResponse create(UserRequest request) {
        // Validar role
        var roleEntity = roleJpaRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Rol", request.roleId()));

        // Validar empleado
        var employee = thirdPartyJpaRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Empleado", request.employeeId()));

        if (employee.getType() != ThirdPartyType.EMPLOYEE) {
            throw new BusinessException("NOT_EMPLOYEE",
                    "El tercero seleccionado no es un empleado. Tipo: " + employee.getType());
        }

        if (!employee.isActive()) {
            throw new BusinessException("INACTIVE_EMPLOYEE",
                    "El empleado seleccionado está inactivo.");
        }

        // Validar que el empleado no tenga ya un usuario
        if (userJpaRepository.existsByEmployeeId(request.employeeId())) {
            throw new BusinessException("EMPLOYEE_HAS_USER",
                    "Este empleado ya tiene un usuario asignado.");
        }

        // Generar username automáticamente del numIdentification
        var username = employee.getNumIdentification();

        // FullName del empleado
        var fullName = employee.getName();

        // Email: el del request si se especifica, si no el del empleado
        var email = request.email() != null && !request.email().isBlank()
                ? request.email()
                : employee.getEmail();

        if (email == null || email.isBlank()) {
            throw new BusinessException("MISSING_EMAIL",
                    "El empleado no tiene email. Especifícalo en el campo email o asígnaselo al empleado.");
        }

        // Generar password temporal
        var tempPassword = generateTempPassword();

        // Crear entidad
        var now = OffsetDateTime.now();
        var entity = new UserEntity();
        entity.setUsername(username);
        entity.setFullName(fullName);
        entity.setEmail(email);
        entity.setRole(roleEntity);
        entity.setActive(request.isActive());
        entity.setEmployee(employee);
        entity.setPasswordHash(passwordEncoder.encode(tempPassword));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        var saved = userJpaRepository.save(entity);
        // Cargar con relaciones para mapeo completo
        var reloaded = userJpaRepository.findById(saved.getId()).orElseThrow();
        var domain = toDomain(reloaded);

        // Enviar email de invitación con enlace para setear contraseña
        try {
            passwordResetUseCase.generateToken(saved.getId());
        } catch (Exception e) {
            log.error("Failed to send set-password email for user {}: {}", saved.getId(), e.getMessage());
            // No fallar la creación del usuario si el email falla
        }

        return UserResponse.from(domain);
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

        // Si cambió el empleado, validar el nuevo
        var newEmployee = thirdPartyJpaRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Empleado", request.employeeId()));

        if (newEmployee.getType() != ThirdPartyType.EMPLOYEE) {
            throw new BusinessException("NOT_EMPLOYEE",
                    "El tercero seleccionado no es un empleado. Tipo: " + newEmployee.getType());
        }

        if (!newEmployee.isActive()) {
            throw new BusinessException("INACTIVE_EMPLOYEE",
                    "El empleado seleccionado está inactivo.");
        }

        // Si cambió el empleado, verificar que el nuevo no tenga otro usuario
        if (!entity.getEmployee().getId().equals(newEmployee.getId())
                && userJpaRepository.existsByEmployeeIdAndIdNot(request.employeeId(), id)) {
            throw new BusinessException("EMPLOYEE_HAS_USER",
                    "El nuevo empleado ya tiene un usuario asignado.");
        }

        entity.setUsername(newEmployee.getNumIdentification());
        entity.setFullName(newEmployee.getName());
        entity.setEmail(request.email() != null && !request.email().isBlank()
                ? request.email()
                : newEmployee.getEmail());
        entity.setRole(newRole);
        entity.setActive(request.isActive());
        entity.setEmployee(newEmployee);
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
        var employee = entity.getEmployee();
        return new User(
                entity.getId(),
                entity.getUsername(),
                entity.getFullName(),
                entity.getEmail(),
                role,
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                employee != null ? employee.getId() : null,
                employee != null ? employee.getName() : null
        );
    }
}
