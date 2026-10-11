package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.SalePayment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
interface SalePaymentMapper {

    SalePayment toDomain(SalePaymentEntity entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    SalePaymentEntity toEntity(SalePayment domain);
}
