package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.ErrorResponse;
import co.posinvent.application.usecase.AccountingPeriodUseCase;
import co.posinvent.domain.repository.CurrentUserProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/accounting/periods")
public class AccountingPeriodController {

    private static final Pattern PERIOD_PATTERN = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private final AccountingPeriodUseCase useCase;
    private final CurrentUserProvider currentUserProvider;

    public AccountingPeriodController(AccountingPeriodUseCase useCase,
                                      CurrentUserProvider currentUserProvider) {
        this.useCase = useCase;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(useCase.list());
    }

    @PostMapping("/{period}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> close(@PathVariable String period) {
        if (!PERIOD_PATTERN.matcher(period).matches()) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("INVALID_PERIOD", "El período debe tener el formato YYYY-MM"));
        }
        var userId = currentUserProvider.currentUserId().orElse(null);
        return ResponseEntity.ok(useCase.close(period, userId));
    }
}
