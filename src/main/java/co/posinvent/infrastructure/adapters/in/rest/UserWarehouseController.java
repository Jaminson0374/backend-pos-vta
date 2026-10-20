package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.AssignWarehouseRequest;
import co.posinvent.application.dto.UserWarehouseResponse;
import co.posinvent.application.usecase.ManageUserWarehousesUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users/{userId}/warehouses")
@PreAuthorize("hasRole('ADMIN')")
public class UserWarehouseController {

    private final ManageUserWarehousesUseCase useCase;

    public UserWarehouseController(ManageUserWarehousesUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<UserWarehouseResponse> list(@PathVariable UUID userId) {
        return useCase.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserWarehouseResponse assign(
            @PathVariable UUID userId,
            @Valid @RequestBody AssignWarehouseRequest request
    ) {
        return useCase.assign(userId, request.warehouseId());
    }

    @DeleteMapping("/{warehouseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID userId, @PathVariable UUID warehouseId) {
        useCase.remove(userId, warehouseId);
    }
}
