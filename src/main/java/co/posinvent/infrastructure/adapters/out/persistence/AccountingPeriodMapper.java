package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.AccountingPeriod;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
interface AccountingPeriodMapper {

    AccountingPeriod toDomain(AccountingPeriodEntity entity);

    AccountingPeriodEntity toEntity(AccountingPeriod domain);
}
