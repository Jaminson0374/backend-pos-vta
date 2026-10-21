package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.ElectronicInvoice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ElectronicInvoiceMapper {

    @Mapping(source = "salesDocument.id", target = "salesDocumentId")
    @Mapping(source = "sourceDocument.id", target = "sourceDocumentId")
    @Mapping(target = "providerResponse", ignore = true)
    @Mapping(target = "providerErrors", ignore = true)
    ElectronicInvoice toDomain(ElectronicInvoiceEntity entity);

    @Mapping(target = "salesDocument", ignore = true)
    @Mapping(target = "sourceDocument", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "providerResponse", ignore = true)
    @Mapping(target = "providerErrors", ignore = true)
    ElectronicInvoiceEntity toEntity(ElectronicInvoice domain);
}
