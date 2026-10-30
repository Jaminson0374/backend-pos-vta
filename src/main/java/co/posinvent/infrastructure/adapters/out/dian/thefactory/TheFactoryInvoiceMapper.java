package co.posinvent.infrastructure.adapters.out.dian.thefactory;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.DianPaymentMeans;
import co.posinvent.domain.model.ElectronicInvoiceRequest;
import co.posinvent.domain.model.ElectronicInvoiceResponse;
import co.posinvent.domain.model.ProviderError;
import co.posinvent.infrastructure.config.FactoryProviderProperties;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Translates the agnostic {@link ElectronicInvoiceRequest} into the SOAP/XML contract of
 * The Factory HKA sandbox and translates its responses back into the domain model.
 *
 * <p>The contract was read from the public WSDL
 * {@code https://demoemision21.thefactoryhka.com.co/ws/v1.0/Service.svc?wsdl} and its XSDs.
 * The invoice root is {@code FacturaGeneral} in the
 * {@code ServiceSoap.UBL2._0.Models.Object} namespace. HKA consumes DIAN codes directly:
 * {@code tipoDocumento=01}, {@code tipoOperacion=05}, {@code codigoTOTALImp=01} for IVA,
 * {@code medioPago=10} for cash, {@code tipoIdentificacion=31} for NIT.</p>
 *
 * <p>All provider-specific field names live here. The adapter and the HTTP client never
 * build invoice XML by hand.</p>
 */
class TheFactoryInvoiceMapper {

    static final String ERROR_CODE = "DIAN_PROVIDER_ERROR";
    static final String CONFIG_MISSING = "DIAN_HKA_CONFIG_MISSING";
    static final String PAYLOAD_INVALID = "DIAN_HKA_PAYLOAD_INVALID";
    static final String PAYMENT_MISMATCH = "DIAN_PAYMENT_TOTAL_MISMATCH";

    static final String ACCEPTED_STATUS = "ACCEPTED_BY_DIAN";
    static final String REJECTED_STATUS = "REJECTED_BY_DIAN";
    static final String SENT_STATUS = "SENT";

    private static final String MODEL_NS =
            "http://schemas.datacontract.org/2004/07/ServiceSoap.UBL2._0.Models.Object";
    private static final String RESPONSE_NS =
            "http://schemas.datacontract.org/2004/07/ServiceSoap.UBL2._0.Response";
    private static final String TEMPURI_NS = "http://tempuri.org/";
    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String ARRAYS_NS =
            "http://schemas.microsoft.com/2003/10/Serialization/Arrays";

    private final FactoryProviderProperties properties;

    TheFactoryInvoiceMapper(FactoryProviderProperties properties) {
        this.properties = properties;
    }

    /** Builds the {@code FacturaGeneral} root, without the SOAP envelope. */
    String toFacturaXml(ElectronicInvoiceRequest request) {
        var document = newDocument();
        var root = element(document, MODEL_NS, "FacturaGeneral");
        document.appendChild(root);

        var items = request.items() == null ? List.<ElectronicInvoiceRequest.InvoiceItem>of() : request.items();
        if (items.isEmpty()) {
            throw new BusinessException(PAYLOAD_INVALID,
                    "La factura no tiene productos para enviar a The Factory HKA.");
        }
        var payments = request.payments() == null
                ? List.<ElectronicInvoiceRequest.Payment>of() : request.payments();
        if (payments.isEmpty()) {
            throw new BusinessException(PAYMENT_MISMATCH,
                    "The Factory HKA exige al menos un medio de pago para la factura.");
        }

        var lines = new ArrayList<LineTotals>(items.size());
        for (var item : items) {
            lines.add(lineTotals(item));
        }
        var net = sum(lines, LineTotals::base);
        var gross = sum(lines, LineTotals::total);
        var lineDiscount = sum(lines, LineTotals::discount);
        var discount = request.discountTotal() != null ? request.discountTotal() : lineDiscount;
        var total = request.totalAmount() != null ? request.totalAmount() : gross;

        appendText(document, root, MODEL_NS, "cantidadDecimales", "2");
        root.appendChild(cliente(document, request));

        appendText(document, root, MODEL_NS, "consecutivoDocumento",
                requirePayload(request.documentNumber(), "documentNumber"));
        root.appendChild(detalleDeFactura(document, items, lines));
        appendText(document, root, MODEL_NS, "fechaEmision",
                normalizeDateTime(requirePayload(request.issueDate(), "issueDate")));
        appendText(document, root, MODEL_NS, "fechaVencimiento",
                dateOnly(firstNonBlank(payments.get(0).dueDate(), request.issueDate())));

        var generalTaxes = impuestosGenerales(document, lines);
        if (generalTaxes != null) {
            root.appendChild(generalTaxes);
        }
        var totalTaxes = impuestosTotales(document, lines);
        if (totalTaxes != null) {
            root.appendChild(totalTaxes);
        }
        var additionalInfo = informacionAdicional(document, request.observations());
        if (additionalInfo != null) {
            root.appendChild(additionalInfo);
        }

        root.appendChild(mediosDePago(document, payments));

        appendText(document, root, MODEL_NS, "moneda",
                firstNonBlank(request.currency(), properties.currency()));
        appendText(document, root, MODEL_NS, "propina", "0.00");
        appendText(document, root, MODEL_NS, "rangoNumeracion",
                requireConfig(properties.numberingRange(), "numbering-range"));
        appendText(document, root, MODEL_NS, "redondeoAplicado", "0.00");
        appendText(document, root, MODEL_NS, "tipoDocumento", properties.documentType());
        appendText(document, root, MODEL_NS, "tipoOperacion", properties.operationType());
        appendText(document, root, MODEL_NS, "totalAnticipos", "0.00");
        appendText(document, root, MODEL_NS, "totalBaseImponible", money(net));
        appendText(document, root, MODEL_NS, "totalBrutoConImpuesto", money(gross));
        appendText(document, root, MODEL_NS, "totalCargosAplicados", "0.00");
        appendText(document, root, MODEL_NS, "totalDescuentos", money(discount));
        appendText(document, root, MODEL_NS, "totalMonto", money(total));
        appendText(document, root, MODEL_NS, "totalProductos", String.valueOf(items.size()));
        appendText(document, root, MODEL_NS, "totalSinImpuestos", money(net));

        return serialize(document);
    }

    /** Builds the full SOAP 1.1 {@code Enviar} envelope, including both HKA tokens. */
    String toEnviarEnvelope(ElectronicInvoiceRequest request) {
        var document = newDocument();
        var envelope = element(document, SOAP_NS, "Envelope");
        document.appendChild(envelope);
        var body = element(document, SOAP_NS, "Body");
        envelope.appendChild(body);
        var enviar = element(document, TEMPURI_NS, "Enviar");
        body.appendChild(enviar);

        appendText(document, enviar, TEMPURI_NS, "tokenEmpresa",
                requireConfig(properties.tokenEmpresa(), "FACTORY_API_TOKEN"));
        appendText(document, enviar, TEMPURI_NS, "tokenPassword",
                requireConfig(properties.tokenPassword(), "FACTORY_API_PASSWORD"));
        var factura = element(document, TEMPURI_NS, "factura");
        factura.appendChild(document.importNode(parse(toFacturaXml(request)).getDocumentElement(), true));
        enviar.appendChild(factura);
        return serialize(document);
    }

    /** Builds the SOAP 1.1 {@code EstadoDocumento} envelope used by the status poller. */
    String toEstadoDocumentoEnvelope(String documento) {
        if (documento == null || documento.isBlank()) {
            throw new BusinessException(PAYLOAD_INVALID,
                    "El CUFE/CUDE es obligatorio para consultar el estado en The Factory HKA.");
        }
        var document = newDocument();
        var envelope = element(document, SOAP_NS, "Envelope");
        document.appendChild(envelope);
        var body = element(document, SOAP_NS, "Body");
        envelope.appendChild(body);
        var estado = element(document, TEMPURI_NS, "EstadoDocumento");
        body.appendChild(estado);
        appendText(document, estado, TEMPURI_NS, "tokenEmpresa",
                requireConfig(properties.tokenEmpresa(), "FACTORY_API_TOKEN"));
        appendText(document, estado, TEMPURI_NS, "tokenPassword",
                requireConfig(properties.tokenPassword(), "FACTORY_API_PASSWORD"));
        appendText(document, estado, TEMPURI_NS, "documento", documento);
        return serialize(document);
    }

    /** Translates an {@code EnviarResponse} or a SOAP Fault into the domain response. */
    ElectronicInvoiceResponse toResponse(String soapXml) {
        var document = parse(soapXml);
        var fault = first(document, "Fault");
        if (fault != null) {
            return faultResponse(fault);
        }
        var result = first(document, "EnviarResult");
        if (result == null) {
            throw new BusinessException(ERROR_CODE,
                    "The Factory HKA no devolvió EnviarResult en la respuesta.");
        }
        var code = text(result, "codigo");
        var status = status(text(result, "esValidoDian"), text(result, "resultado"), code);
        var reference = firstNonBlank(
                text(result, "consecutivoDocumento"),
                text(result, "hash"),
                text(result, "nombre"));
        return new ElectronicInvoiceResponse(
                firstNonBlank(text(result, "cufe"), text(result, "hash")),
                text(result, "qr"),
                status,
                reference,
                reference,
                null,
                null,
                validationErrors(result, status, code, text(result, "mensaje")));
    }

    /** Translates an {@code EstadoDocumentoResponse} or a SOAP Fault. */
    ElectronicInvoiceResponse toStatusResponse(String soapXml) {
        var document = parse(soapXml);
        var fault = first(document, "Fault");
        if (fault != null) {
            return faultResponse(fault);
        }
        var result = first(document, "EstadoDocumentoResult");
        if (result == null) {
            throw new BusinessException(ERROR_CODE,
                    "The Factory HKA no devolvió EstadoDocumentoResult en la respuesta.");
        }
        var code = text(result, "codigo");
        var status = status(text(result, "esValidoDIAN"), text(result, "resultado"), code);
        var reference = firstNonBlank(text(result, "consecutivo"), text(result, "trackID"));
        return new ElectronicInvoiceResponse(
                firstNonBlank(text(result, "cufe"), text(result, "cadenaCufe")),
                text(result, "cadenaCodigoQR"),
                status,
                reference,
                reference,
                null,
                null,
                validationErrors(result, status, code,
                        firstNonBlank(text(result, "mensaje"), text(result, "mensajeDocumento"))));
    }

    private Element cliente(Document document, ElectronicInvoiceRequest request) {
        var client = request.client();
        var element = element(document, MODEL_NS, "cliente");

        var name = client == null ? properties.finalConsumerName()
                : firstNonBlank(join(client.name(), client.lastName()), client.identification());
        var personType = client == null ? "2" : personType(client.personType());
        var identificationType = client == null
                ? properties.finalConsumerIdentificationType()
                : firstNonBlank(client.identificationTypeCode(),
                        properties.finalConsumerIdentificationType());
        var identification = client == null
                ? properties.finalConsumerIdentification()
                : firstNonBlank(client.identification(),
                        properties.finalConsumerIdentification());
        var dv = client == null ? null : client.dv();

        var address = direccionCliente(document, client);
        if (address != null) {
            element.appendChild(address);
        }
        appendText(document, element, MODEL_NS, "email", client == null ? null : client.email());
        appendText(document, element, MODEL_NS, "nombreComercial", name);
        appendText(document, element, MODEL_NS, "nombreRazonSocial", name);
        appendText(document, element, MODEL_NS, "notificar", "SI");
        appendText(document, element, MODEL_NS, "numeroDocumento", identification);
        appendText(document, element, MODEL_NS, "numeroIdentificacionDV", dv);
        var responsibilities = responsabilidadesRut(document, client);
        if (responsibilities != null) {
            element.appendChild(responsibilities);
        }
        appendText(document, element, MODEL_NS, "telefono", client == null ? null : client.phone());
        appendText(document, element, MODEL_NS, "tipoIdentificacion", identificationType);
        appendText(document, element, MODEL_NS, "tipoPersona", personType);
        return element;
    }

    private Element direccionCliente(Document document, ElectronicInvoiceRequest.ClientInfo client) {
        if (client == null) {
            return null;
        }
        if (isBlank(client.address()) && isBlank(client.cityName())
                && isBlank(client.municipalityCode()) && isBlank(client.departmentCode())
                && isBlank(client.departmentName())) {
            return null;
        }
        var element = element(document, MODEL_NS, "direccionCliente");
        appendText(document, element, MODEL_NS, "ciudad", client.cityName());
        appendText(document, element, MODEL_NS, "codigoDepartamento", client.departmentCode());
        appendText(document, element, MODEL_NS, "departamento", client.departmentName());
        appendText(document, element, MODEL_NS, "direccion", client.address());
        appendText(document, element, MODEL_NS, "lenguaje", "ES");
        appendText(document, element, MODEL_NS, "municipio", client.municipalityCode());
        appendText(document, element, MODEL_NS, "pais",
                firstNonBlank(client.countryCode(), "CO"));
        appendText(document, element, MODEL_NS, "zonaPostal", client.postalCode());
        return element;
    }

    private Element responsabilidadesRut(Document document,
                                         ElectronicInvoiceRequest.ClientInfo client) {
        if (client == null || isBlank(client.taxLevelCode())) {
            return null;
        }
        var wrapper = element(document, MODEL_NS, "responsabilidadesRut");
        for (var code : client.taxLevelCode().split("[;,]")) {
            if (code.isBlank()) {
                continue;
            }
            var obligation = element(document, MODEL_NS, "Obligaciones");
            appendText(document, obligation, MODEL_NS, "obligaciones", code.trim());
            wrapper.appendChild(obligation);
        }
        return wrapper;
    }

    private Element detalleDeFactura(Document document,
                                     List<ElectronicInvoiceRequest.InvoiceItem> items,
                                     List<LineTotals> lines) {
        var wrapper = element(document, MODEL_NS, "detalleDeFactura");
        for (int index = 0; index < items.size(); index++) {
            var item = items.get(index);
            var line = lines.get(index);
            var detail = element(document, MODEL_NS, "FacturaDetalle");

            appendText(document, detail, MODEL_NS, "cantidadPorEmpaque", "1");
            appendText(document, detail, MODEL_NS, "cantidadReal", quantity(item.quantity()));
            appendText(document, detail, MODEL_NS, "cantidadRealUnidadMedida",
                    properties.unitMeasure());
            appendText(document, detail, MODEL_NS, "cantidadUnidades", quantity(item.quantity()));
            if (line.discount().signum() > 0) {
                var discounts = element(document, MODEL_NS, "cargosDescuentos");
                discounts.appendChild(cargoDescuento(document, line, index + 1));
                detail.appendChild(discounts);
            }
            appendText(document, detail, MODEL_NS, "codigoIdentificadorPais", "CO");
            appendText(document, detail, MODEL_NS, "codigoProducto",
                    firstNonBlank(item.productCode(), "SIN-CODIGO"));
            appendText(document, detail, MODEL_NS, "codigoTipoPrecio", "01");
            appendText(document, detail, MODEL_NS, "descripcion",
                    firstNonBlank(item.productName(), item.productCode(), "SIN-CODIGO"));
            if (line.taxed()) {
                var taxes = element(document, MODEL_NS, "impuestosDetalles");
                taxes.appendChild(facturaImpuesto(document, line.taxCode(), line.rate(),
                        line.base(), line.tax()));
                detail.appendChild(taxes);

                var totals = element(document, MODEL_NS, "impuestosTotales");
                totals.appendChild(impuestoTotal(document, line.taxCode(), line.tax()));
                detail.appendChild(totals);
            }
            appendText(document, detail, MODEL_NS, "muestraGratis", "0");
            appendText(document, detail, MODEL_NS, "precioReferencia", "0.00");
            appendText(document, detail, MODEL_NS, "precioTotal", money(line.total()));
            appendText(document, detail, MODEL_NS, "precioTotalSinImpuestos", money(line.base()));
            appendText(document, detail, MODEL_NS, "precioVentaUnitario", money(item.unitPrice()));
            appendText(document, detail, MODEL_NS, "secuencia", String.valueOf(index + 1));
            appendText(document, detail, MODEL_NS, "unidadMedida", properties.unitMeasure());
            wrapper.appendChild(detail);
        }
        return wrapper;
    }

    private Element impuestosGenerales(Document document, List<LineTotals> lines) {
        var groups = new LinkedHashMap<String, TaxGroup>();
        for (var line : lines) {
            if (!line.taxed()) {
                continue;
            }
            var key = line.taxCode() + "|" + line.rate().stripTrailingZeros().toPlainString();
            groups.computeIfAbsent(key, ignored -> new TaxGroup(line.taxCode(), line.rate()))
                    .add(line.base(), line.tax());
        }
        if (groups.isEmpty()) {
            return null;
        }
        var wrapper = element(document, MODEL_NS, "impuestosGenerales");
        for (var group : groups.values()) {
            wrapper.appendChild(facturaImpuesto(document, group.code, group.rate,
                    group.base, group.value));
        }
        return wrapper;
    }

    private Element impuestosTotales(Document document, List<LineTotals> lines) {
        var totals = new LinkedHashMap<String, BigDecimal>();
        for (var line : lines) {
            if (!line.taxed()) {
                continue;
            }
            totals.merge(line.taxCode(), line.tax(), BigDecimal::add);
        }
        if (totals.isEmpty()) {
            return null;
        }
        var wrapper = element(document, MODEL_NS, "impuestosTotales");
        for (var entry : totals.entrySet()) {
            wrapper.appendChild(impuestoTotal(document, entry.getKey(), entry.getValue()));
        }
        return wrapper;
    }

    private Element informacionAdicional(Document document, String observations) {
        if (isBlank(observations)) {
            return null;
        }
        var wrapper = element(document, MODEL_NS, "informacionAdicional");
        var value = element(document, ARRAYS_NS, "string");
        value.setTextContent(observations);
        wrapper.appendChild(value);
        return wrapper;
    }

    private Element mediosDePago(Document document,
                                 List<ElectronicInvoiceRequest.Payment> payments) {
        var wrapper = element(document, MODEL_NS, "mediosDePago");
        for (var payment : payments) {
            var element = element(document, MODEL_NS, "MediosDePago");
            appendText(document, element, MODEL_NS, "fechaDeVencimiento", dateOnly(payment.dueDate()));
            appendText(document, element, MODEL_NS, "medioPago", paymentCode(payment));
            appendText(document, element, MODEL_NS, "metodoDePago", properties.paymentMethod());
            wrapper.appendChild(element);
        }
        return wrapper;
    }

    private Element facturaImpuesto(Document document, String code, BigDecimal rate,
                                    BigDecimal base, BigDecimal value) {
        var element = element(document, MODEL_NS, "FacturaImpuestos");
        appendText(document, element, MODEL_NS, "baseImponibleTOTALImp", money(base));
        appendText(document, element, MODEL_NS, "codigoTOTALImp", code);
        appendText(document, element, MODEL_NS, "porcentajeTOTALImp", percentage(rate));
        appendText(document, element, MODEL_NS, "unidadMedida", properties.unitMeasure());
        appendText(document, element, MODEL_NS, "unidadMedidaTributo", "0.00");
        appendText(document, element, MODEL_NS, "valorTOTALImp", money(value));
        appendText(document, element, MODEL_NS, "valorTributoUnidad", "0.00");
        return element;
    }

    private Element impuestoTotal(Document document, String code, BigDecimal value) {
        var element = element(document, MODEL_NS, "ImpuestosTotales");
        appendText(document, element, MODEL_NS, "codigoTOTALImp", code);
        appendText(document, element, MODEL_NS, "montoTotal", money(value));
        return element;
    }

    private Element cargoDescuento(Document document, LineTotals line, int sequence) {
        var element = element(document, MODEL_NS, "CargosDescuentos");
        appendText(document, element, MODEL_NS, "codigo", properties.discountCode());
        appendText(document, element, MODEL_NS, "descripcion", "Descuento");
        appendText(document, element, MODEL_NS, "indicador", "0");
        appendText(document, element, MODEL_NS, "monto", money(line.discount()));
        appendText(document, element, MODEL_NS, "montoBase", money(line.gross()));
        appendText(document, element, MODEL_NS, "porcentaje",
                percentage(line.discount(), line.gross()));
        appendText(document, element, MODEL_NS, "secuencia", String.valueOf(sequence));
        return element;
    }

    private LineTotals lineTotals(ElectronicInvoiceRequest.InvoiceItem item) {
        var quantity = item.quantity() == null ? BigDecimal.ZERO : item.quantity();
        var unitPrice = item.unitPrice() == null ? BigDecimal.ZERO : item.unitPrice();
        var gross = item.subtotal() != null ? item.subtotal() : quantity.multiply(unitPrice);
        var discount = item.discountAmount() == null ? BigDecimal.ZERO : item.discountAmount();
        if (discount.signum() < 0) {
            discount = BigDecimal.ZERO;
        }
        var base = gross.subtract(discount);
        if (base.signum() < 0) {
            base = BigDecimal.ZERO;
        }
        var rate = item.taxRate() == null ? BigDecimal.ZERO : item.taxRate();
        var taxed = item.taxRate() != null
                || (item.taxAmount() != null && item.taxAmount().signum() != 0);
        var tax = item.taxAmount() != null
                ? item.taxAmount()
                : base.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new LineTotals(base, tax, base.add(tax), gross, discount, rate,
                taxCode(item), taxed);
    }

    private String taxCode(ElectronicInvoiceRequest.InvoiceItem item) {
        var type = item.taxType() == null ? "" : item.taxType().toUpperCase(Locale.ROOT);
        return type.contains("INC") ? properties.taxCodeInc() : properties.taxCodeIva();
    }

    private String paymentCode(ElectronicInvoiceRequest.Payment payment) {
        if (!isBlank(payment.dianCode())) {
            return payment.dianCode().trim();
        }
        return DianPaymentMeans.fromInternal(payment.method()).code();
    }

    private static BigDecimal sum(List<LineTotals> lines,
                                  java.util.function.Function<LineTotals, BigDecimal> getter) {
        var total = BigDecimal.ZERO;
        for (var line : lines) {
            total = total.add(getter.apply(line));
        }
        return total;
    }

    private static String personType(String value) {
        if (value == null || value.isBlank()) {
            return "1";
        }
        var normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("1") || normalized.equals("COMPANY")
                || normalized.equals("JURIDICA") || normalized.equals("JURÍDICA")) {
            return "1";
        }
        return "2";
    }

    private static String status(String validFlag, String resultado, String code) {
        if (parseBoolean(validFlag)) {
            return ACCEPTED_STATUS;
        }
        var normalized = resultado == null ? "" : resultado.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("acept") || normalized.contains("aprob")
                || normalized.contains("valid") || normalized.contains("exitos")
                || normalized.equals("ok")) {
            return ACCEPTED_STATUS;
        }
        if (normalized.contains("rechaz") || normalized.contains("error")
                || normalized.contains("fall") || normalized.contains("anul")) {
            return REJECTED_STATUS;
        }
        if (parseInt(code) > 0) {
            return REJECTED_STATUS;
        }
        return SENT_STATUS;
    }

    private static List<ProviderError> validationErrors(Element result, String status,
                                                        String code, String message) {
        var errors = new ArrayList<ProviderError>();
        var messages = child(result, "mensajesValidacion");
        if (messages == null) {
            messages = child(result, "reglasValidacionDIAN");
        }
        if (messages != null) {
            for (var element : descendants(messages, "string")) {
                var text = element.getTextContent() == null ? "" : element.getTextContent().trim();
                if (!text.isEmpty()) {
                    errors.add(new ProviderError("HKA_VALIDATION", text, List.of(), null));
                }
            }
        }
        if (errors.isEmpty() && REJECTED_STATUS.equals(status)) {
            errors.add(new ProviderError(
                    code == null || code.isBlank() ? "HKA_REJECTED" : "HKA_" + code,
                    firstNonBlank(message, "The Factory HKA rechazó la factura."),
                    List.of(),
                    null));
        }
        return errors;
    }

    private static ElectronicInvoiceResponse faultResponse(Element fault) {
        return new ElectronicInvoiceResponse(
                null, null, REJECTED_STATUS, null, null, null, null,
                List.of(new ProviderError(
                        "HKA_SOAP_FAULT",
                        firstNonBlank(text(fault, "faultstring"), text(fault, "Reason"),
                                "The Factory HKA devolvió un SOAP Fault."),
                        List.of(),
                        text(fault, "faultcode"))));
    }

    private static String requireConfig(String value, String property) {
        if (isBlank(value)) {
            throw new BusinessException(CONFIG_MISSING,
                    "Falta " + property + " en la configuración de The Factory HKA.");
        }
        return value;
    }

    private static String requirePayload(String value, String field) {
        if (isBlank(value)) {
            throw new BusinessException(PAYLOAD_INVALID,
                    "El campo " + field + " es obligatorio para The Factory HKA.");
        }
        return value;
    }

    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String quantity(BigDecimal value) {
        return money(value);
    }

    private static String percentage(BigDecimal value) {
        return money(value);
    }

    private static String percentage(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return "0.00";
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total, 2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String normalizeDateTime(String value) {
        if (isBlank(value)) {
            return null;
        }
        var normalized = value.trim().replace('T', ' ');
        if (normalized.length() == 10) {
            return normalized + " 00:00:00";
        }
        return normalized.length() > 19 ? normalized.substring(0, 19) : normalized;
    }

    private static String dateOnly(String value) {
        if (isBlank(value)) {
            return null;
        }
        var normalized = value.trim();
        return normalized.length() >= 10 ? normalized.substring(0, 10) : normalized;
    }

    private static String join(String first, String second) {
        var parts = new ArrayList<String>(2);
        if (!isBlank(first)) {
            parts.add(first.trim());
        }
        if (!isBlank(second)) {
            parts.add(second.trim());
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (var value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("true") || normalized.equals("1")
                || normalized.equals("si") || normalized.equals("sí");
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Document newDocument() {
        try {
            return secureFactory().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("No se pudo crear el documento XML", e);
        }
    }

    private static Document parse(String xml) {
        if (isBlank(xml)) {
            throw new BusinessException(ERROR_CODE,
                    "The Factory HKA devolvió una respuesta vacía.");
        }
        try {
            return secureFactory().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException(ERROR_CODE,
                    "Respuesta XML inválida de The Factory HKA: " + e.getMessage());
        }
    }

    private static DocumentBuilderFactory secureFactory() throws ParserConfigurationException {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private static String serialize(Document document) {
        try {
            var factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            var writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new BusinessException(ERROR_CODE,
                    "No se pudo serializar la factura para The Factory HKA: " + e.getMessage());
        }
    }

    private static Element element(Document document, String namespace, String name) {
        return document.createElementNS(namespace, name);
    }

    private static void appendText(Document document, Element parent, String namespace,
                                   String name, String value) {
        if (isBlank(value)) {
            return;
        }
        var element = element(document, namespace, name);
        element.setTextContent(value);
        parent.appendChild(element);
    }

    private static Element first(Document document, String localName) {
        var nodes = document.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static Element child(Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && localName.equals(node.getLocalName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static String text(Element parent, String localName) {
        var element = child(parent, localName);
        if (element == null) {
            var elements = descendants(parent, localName);
            element = elements.isEmpty() ? null : elements.get(0);
        }
        return element == null ? null : element.getTextContent().trim();
    }

    private static List<Element> descendants(Element parent, String localName) {
        var result = new ArrayList<Element>();
        if (parent == null) {
            return result;
        }
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add((Element) nodes.item(i));
        }
        return result;
    }

    private record LineTotals(
            BigDecimal base,
            BigDecimal tax,
            BigDecimal total,
            BigDecimal gross,
            BigDecimal discount,
            BigDecimal rate,
            String taxCode,
            boolean taxed
    ) {}

    private static final class TaxGroup {
        private final String code;
        private final BigDecimal rate;
        private BigDecimal base = BigDecimal.ZERO;
        private BigDecimal value = BigDecimal.ZERO;

        private TaxGroup(String code, BigDecimal rate) {
            this.code = code;
            this.rate = rate;
        }

        private void add(BigDecimal base, BigDecimal value) {
            this.base = this.base.add(base);
            this.value = this.value.add(value);
        }
    }
}
