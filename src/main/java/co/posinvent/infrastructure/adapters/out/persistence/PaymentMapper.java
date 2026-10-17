package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
interface PaymentMapper {

    @Mapping(target = "isAdvance", source = "advance")
    Payment toDomain(PaymentEntity entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "advance", source = "isAdvance")
    PaymentEntity toEntity(Payment domain);
}
