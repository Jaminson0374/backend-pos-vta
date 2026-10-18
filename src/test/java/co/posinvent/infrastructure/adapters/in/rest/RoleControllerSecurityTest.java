package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.domain.model.Role;
import co.posinvent.infrastructure.adapters.out.persistence.RoleEntity;
import co.posinvent.infrastructure.adapters.out.persistence.RoleJpaRepository;
import co.posinvent.infrastructure.adapters.out.persistence.RoleMapper;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import co.posinvent.infrastructure.config.JwtAuthFilter;
import co.posinvent.application.service.JwtBlacklistService;
import co.posinvent.infrastructure.config.GlobalExceptionHandler;
import co.posinvent.infrastructure.config.JwtService;
import co.posinvent.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the static role catalog. The list endpoint stays ADMIN-only and no longer
 * exposes the removed {@code permissions} JSONB; the old update endpoint is gone.
 */
@WebMvcTest(controllers = RoleController.class,
        properties = "spring.web.resources.add-mappings=false",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = GlobalExceptionHandler.class))
@ActiveProfiles("test")
@Import({SecurityConfig.class, RoleControllerSecurityTest.JwtFilterTestConfig.class})
class RoleControllerSecurityTest {

    private static final UUID ROLE_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleJpaRepository roleJpaRepository;

    @MockitoBean
    private RoleMapper roleMapper;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;

    @TestConfiguration
    static class JwtFilterTestConfig {
        @Bean
        JwtAuthFilter jwtAuthFilter(JwtService jwtService, UserDetailsService userDetailsService,
                                    JwtBlacklistService jwtBlacklistService) {
            return new JwtAuthFilter(jwtService, userDetailsService, jwtBlacklistService);
        }
    }

    private static RequestPostProcessor asRole(String role) {
        var principal = new PosUserDetails(
                UUID.randomUUID(),
                "user-" + role.toLowerCase(),
                "irrelevant-hash",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)),
                null,
                0L
        );
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("ADMIN can list roles and the payload has no permissions (GET / -> 200)")
    void adminCanListRolesWithoutPermissions() throws Exception {
        var entity = new RoleEntity();
        entity.setId(ROLE_ID);
        entity.setName("ADMIN");
        entity.setCreatedAt(OffsetDateTime.now());

        given(roleJpaRepository.findAll()).willReturn(List.of(entity));
        given(roleMapper.toDomain(entity)).willReturn(new Role(ROLE_ID, "ADMIN", entity.getCreatedAt()));

        mockMvc.perform(get("/api/v1/admin/roles").with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("ADMIN"))
                .andExpect(jsonPath("$[0].permissions").doesNotExist());

        then(roleJpaRepository).should().findAll();
    }

    @Test
    @DisplayName("CAJERO cannot list roles (GET / -> 403)")
    void cajeroCannotListRoles() throws Exception {
        mockMvc.perform(get("/api/v1/admin/roles").with(asRole("CAJERO")))
                .andExpect(status().isForbidden());

        then(roleJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("The removed role update route no longer exists (PUT /{id} -> 404)")
    void updateRoleEndpointIsGone() throws Exception {
        mockMvc.perform(put("/api/v1/admin/roles/{id}", ROLE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions":"[\\"READ\\"]"}
                                """)
                        .with(asRole("ADMIN")))
                .andExpect(status().isNotFound());

        then(roleJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("The role collection only supports reads (PUT / -> 405)")
    void roleCollectionRejectsWrites() throws Exception {
        mockMvc.perform(put("/api/v1/admin/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions":"[\\"READ\\"]"}
                                """)
                        .with(asRole("ADMIN")))
                .andExpect(status().isMethodNotAllowed());

        then(roleJpaRepository).shouldHaveNoInteractions();
    }
}
