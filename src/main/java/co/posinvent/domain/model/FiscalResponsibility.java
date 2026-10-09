package co.posinvent.domain.model;

import java.util.List;
import java.util.UUID;

public record FiscalResponsibility(UUID id, String code, String name, List<String> excludes, int sortOrder) {}
