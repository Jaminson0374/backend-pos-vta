package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.PucAccountRequest;
import co.posinvent.application.dto.PucAccountResponse;
import co.posinvent.application.usecase.PucAccountUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/puc-accounts")
public class PucAccountController {

    private final PucAccountUseCase useCase;

    public PucAccountController(PucAccountUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<PucAccountResponse> listAll(@RequestParam(required = false) Integer accountClass) {
        return accountClass != null ? useCase.listByClass(accountClass) : useCase.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public PucAccountResponse getById(@PathVariable UUID id) {
        return useCase.getById(id);
    }

    @GetMapping("/tree")
    @PreAuthorize("isAuthenticated()")
    public List<PucAccountResponse> tree(@RequestParam(required = false) String search) {
        return useCase.tree(search);
    }

    @GetMapping("/check-code")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> checkCode(@RequestParam String code) {
        boolean exists = useCase.existsByCode(code);
        return Map.of("code", code, "available", !exists);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public PucAccountResponse create(@Valid @RequestBody PucAccountRequest request) {
        return useCase.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PucAccountResponse update(@PathVariable UUID id, @Valid @RequestBody PucAccountRequest request) {
        return useCase.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@PathVariable UUID id) {
        useCase.deactivate(id);
    }
}
