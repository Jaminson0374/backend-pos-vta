package co.posinvent.domain.repository;

import co.posinvent.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(UUID id);
    Optional<User> findByUsername(String username);
    Page<User> findAll(Pageable pageable);
    Page<User> findFiltered(String searchPattern, String roleName, Boolean active, Pageable pageable);
    long countByRoleNameAndActive(String roleName, boolean active);
}
