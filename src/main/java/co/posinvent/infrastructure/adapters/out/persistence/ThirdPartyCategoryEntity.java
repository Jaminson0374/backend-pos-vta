package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "third_party_categories")
@Getter
@Setter
public class ThirdPartyCategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "base_type", nullable = false, length = 20)
    private String baseType;

    @Column(name = "active", nullable = false)
    private boolean active;
}
