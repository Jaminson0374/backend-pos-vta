package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_warehouses")
@Getter
@Setter
@NoArgsConstructor
public class UserWarehouseEntity {

    @EmbeddedId
    private UserWarehouseId id;

    public UserWarehouseEntity(UserWarehouseId id) {
        this.id = id;
    }
}
