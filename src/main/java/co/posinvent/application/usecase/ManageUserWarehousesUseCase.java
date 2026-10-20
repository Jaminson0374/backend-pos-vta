package co.posinvent.application.usecase;

import co.posinvent.application.dto.UserWarehouseResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.repository.UserRepository;
import co.posinvent.domain.repository.UserWarehouseRepository;
import co.posinvent.domain.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ManageUserWarehousesUseCase {

    private final UserWarehouseRepository membershipRepo;
    private final UserRepository userRepo;
    private final WarehouseRepository warehouseRepo;

    public ManageUserWarehousesUseCase(
            UserWarehouseRepository membershipRepo,
            UserRepository userRepo,
            WarehouseRepository warehouseRepo
    ) {
        this.membershipRepo = membershipRepo;
        this.userRepo = userRepo;
        this.warehouseRepo = warehouseRepo;
    }

    @Transactional(readOnly = true)
    public List<UserWarehouseResponse> list(UUID userId) {
        requireUser(userId);
        return membershipRepo.findWarehouseIdsByUserId(userId).stream()
                .map(warehouseId -> new UserWarehouseResponse(userId, warehouseId))
                .toList();
    }

    @Transactional
    public UserWarehouseResponse assign(UUID userId, UUID warehouseId) {
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));
        if (!user.isActive()) {
            throw new BusinessException("USER_INACTIVE", "El usuario está inactivo.");
        }

        var warehouse = warehouseRepo.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Bodega", warehouseId));
        if (!warehouse.active()) {
            throw new BusinessException("WAREHOUSE_INACTIVE", "La bodega está inactiva.");
        }

        membershipRepo.assign(userId, warehouseId);
        return new UserWarehouseResponse(userId, warehouseId);
    }

    @Transactional
    public void remove(UUID userId, UUID warehouseId) {
        membershipRepo.remove(userId, warehouseId);
    }

    private void requireUser(UUID userId) {
        userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));
    }
}
