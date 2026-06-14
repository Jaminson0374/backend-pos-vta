package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.AccountingTemplateRequest;
import co.posinvent.application.dto.AccountingTemplateResponse;
import co.posinvent.application.usecase.AccountingTemplateUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounting-templates")
public class AccountingTemplateController {

    private final AccountingTemplateUseCase useCase;

    public AccountingTemplateController(AccountingTemplateUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR')")
    public List<AccountingTemplateResponse> list(@RequestParam(required = false) String module) {
        return useCase.list(module);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTADOR')")
    public AccountingTemplateResponse getById(@PathVariable UUID id) {
        return useCase.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AccountingTemplateResponse create(@Valid @RequestBody AccountingTemplateRequest request) {
        return useCase.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountingTemplateResponse update(@PathVariable UUID id,
                                              @Valid @RequestBody AccountingTemplateRequest request) {
        return useCase.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        useCase.delete(id);
    }
}
