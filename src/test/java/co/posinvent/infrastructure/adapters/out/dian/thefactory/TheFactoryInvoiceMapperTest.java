package co.posinvent.infrastructure.adapters.out.dian.thefactory;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.ElectronicInvoiceRequest;
import co.posinvent.infrastructure.config.FactoryProviderProperties;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Unit tests for {@link TheFactoryInvoiceMapper}. They assert the XML contract published by
 * the HKA sandbox WSDL ({@code FacturaGeneral}), never a hand-made JSON shape.
 */
class TheFactoryInvoiceMapperTest {

    private static final String MODEL_NS =
            "http://schemas.datacontract.org/2004/07/ServiceSoap.UBL2._0.Models.Object";

    private final TheFactoryInvoiceMapper mapper = new TheFactoryInvoiceMapper(props());

    @Test
    void mapsAgnosticRequestToHkaFacturaXml() {
        var xml = mapper.toFacturaXml(sampleRequest());
        var document = parse(xml);

        assertThat(document.getDocumentElement().getLocalName()).isEqualTo("FacturaGeneral");
        assertThat(document.getDocumentElement().getNamespaceURI()).isEqualTo(MODEL_NS);
        assertThat(text(document, "rangoNumeracion")).isEqualTo("FV-1");
        assertThat(text(document, "consecutivoDocumento")).isEqualTo("1");
        assertThat(text(document, "fechaEmision")).isEqualTo("2026-10-29 00:00:00");
        assertThat(text(document, "tipoDocumento")).isEqualTo("01");
        assertThat(text(document, "tipoOperacion")).isEqualTo("05");
        assertThat(text(document, "moneda")).isEqualTo("COP");
        assertThat(text(document, "totalMonto")).isEqualTo("290500.00");
        assertThat(text(document, "totalSinImpuestos")).isEqualTo("250000.00");
        assertThat(text(document, "totalBaseImponible")).isEqualTo("250000.00");
        assertThat(text(document, "totalBrutoConImpuesto")).isEqualTo("290500.00");
        assertThat(text(document, "totalDescuentos")).isEqualTo("0.00");
        assertThat(text(document, "totalProductos")).isEqualTo("2");

        var cliente = child(document.getDocumentElement(), "cliente");
        assertThat(childText(cliente, "nombreRazonSocial")).isEqualTo("ACME SAS");
        assertThat(childText(cliente, "tipoPersona")).isEqualTo("1");
        assertThat(childText(cliente, "tipoIdentificacion")).isEqualTo("31");
        assertThat(childText(cliente, "numeroDocumento")).isEqualTo("900123456");
        assertThat(childText(cliente, "numeroIdentificacionDV")).isEqualTo("1");
        assertThat(childText(cliente, "email")).isEqualTo("facturas@acme.test");
        assertThat(childText(child(cliente, "direccionCliente"), "municipio")).isEqualTo("11001");
        assertThat(childText(child(cliente, "direccionCliente"), "ciudad")).isEqualTo("BOGOTA");
        assertThat(descendants(child(cliente, "responsabilidadesRut"), "Obligaciones"))
                .extracting(element -> childText(element, "obligaciones"))
                .containsExactly("O-13", "O-15");

        var details = descendants(document, "FacturaDetalle");
        assertThat(details).hasSize(2);
        var first = details.get(0);
        assertThat(childText(first, "secuencia")).isEqualTo("1");
        assertThat(childText(first, "codigoProducto")).isEqualTo("P1");
        assertThat(childText(first, "cantidadUnidades")).isEqualTo("2.00");
        assertThat(childText(first, "precioVentaUnitario")).isEqualTo("100000.00");
        assertThat(childText(first, "precioTotalSinImpuestos")).isEqualTo("200000.00");
        assertThat(childText(first, "precioTotal")).isEqualTo("238000.00");
        var firstTax = child(child(first, "impuestosDetalles"), "FacturaImpuestos");
        assertThat(childText(firstTax, "codigoTOTALImp")).isEqualTo("01");
        assertThat(childText(firstTax, "porcentajeTOTALImp")).isEqualTo("19.00");
        assertThat(childText(firstTax, "baseImponibleTOTALImp")).isEqualTo("200000.00");
        assertThat(childText(firstTax, "valorTOTALImp")).isEqualTo("38000.00");

        var generalTaxes = descendants(child(document.getDocumentElement(), "impuestosGenerales"),
                "FacturaImpuestos");
        assertThat(generalTaxes).hasSize(2);
        assertThat(generalTaxes)
                .extracting(element -> childText(element, "porcentajeTOTALImp"))
                .containsExactly("19.00", "5.00");

        var totalTaxes = descendants(child(document.getDocumentElement(), "impuestosTotales"),
                "ImpuestosTotales");
        assertThat(totalTaxes).hasSize(1);
        assertThat(childText(totalTaxes.get(0), "codigoTOTALImp")).isEqualTo("01");
        assertThat(childText(totalTaxes.get(0), "montoTotal")).isEqualTo("40500.00");

        var payments = descendants(child(document.getDocumentElement(), "mediosDePago"),
                "MediosDePago");
        assertThat(payments).hasSize(1);
        assertThat(childText(payments.get(0), "medioPago")).isEqualTo("10");
        assertThat(childText(payments.get(0), "metodoDePago")).isEqualTo("1");
        assertThat(childText(payments.get(0), "fechaDeVencimiento")).isEqualTo("2026-10-29");
    }

    @Test
    void mapsIncTaxAndCardPaymentToDianCodes() {
        var request = new ElectronicInvoiceRequest(
                "2", "2026-10-29", "900123456", "ACME SAS",
                List.of(new ElectronicInvoiceRequest.InvoiceItem(
                        "P2", "Bebida", BigDecimal.ONE, new BigDecimal("100000"),
                        "INC", new BigDecimal("8"), new BigDecimal("8000"),
                        new BigDecimal("100000"), BigDecimal.ZERO)),
                Map.of("8", new BigDecimal("8000")),
                new BigDecimal("108000"), companyInfo(), BigDecimal.ZERO,
                client(), List.of(new ElectronicInvoiceRequest.Payment(
                        "CARD", "48", new BigDecimal("108000"), "2026-10-29")),
                "COP", null);

        var document = parse(mapper.toFacturaXml(request));

        var taxes = descendants(document, "FacturaImpuestos");
        assertThat(taxes).extracting(element -> childText(element, "codigoTOTALImp"))
                .containsOnly("04");
        var payment = descendants(document, "MediosDePago").get(0);
        assertThat(childText(payment, "medioPago")).isEqualTo("48");
    }

    @Test
    void appliesLineDiscountsToNetAmountsAndExplainsThem() {
        var request = new ElectronicInvoiceRequest(
                "3", "2026-10-29", "900123456", "ACME SAS",
                List.of(new ElectronicInvoiceRequest.InvoiceItem(
                        "P3", "Monitor", new BigDecimal("2"), new BigDecimal("100000"),
                        "IVA", new BigDecimal("19"), new BigDecimal("34200"),
                        new BigDecimal("200000"), new BigDecimal("20000"))),
                Map.of("19", new BigDecimal("34200")),
                new BigDecimal("214200"), companyInfo(), new BigDecimal("20000"),
                client(), List.of(new ElectronicInvoiceRequest.Payment(
                        "CASH", "10", new BigDecimal("214200"), "2026-10-29")),
                "COP", null);

        var document = parse(mapper.toFacturaXml(request));
        var detail = descendants(document, "FacturaDetalle").get(0);

        assertThat(childText(detail, "precioTotalSinImpuestos")).isEqualTo("180000.00");
        assertThat(childText(detail, "precioTotal")).isEqualTo("214200.00");
        assertThat(text(document, "totalBaseImponible")).isEqualTo("180000.00");
        assertThat(text(document, "totalDescuentos")).isEqualTo("20000.00");
        var discount = child(detail, "cargosDescuentos");
        var lineDiscount = child(discount, "CargosDescuentos");
        assertThat(childText(lineDiscount, "indicador")).isEqualTo("0");
        assertThat(childText(lineDiscount, "monto")).isEqualTo("20000.00");
        assertThat(childText(lineDiscount, "montoBase")).isEqualTo("200000.00");
    }

    @Test
    void usesFinalConsumerFallbackWhenClientIsMissing() {
        var request = new ElectronicInvoiceRequest(
                "4", "2026-10-29", null, null,
                List.of(new ElectronicInvoiceRequest.InvoiceItem(
                        "P4", "Producto", BigDecimal.ONE, new BigDecimal("10000"),
                        "IVA", new BigDecimal("19"), new BigDecimal("1900"),
                        new BigDecimal("10000"), BigDecimal.ZERO)),
                Map.of("19", new BigDecimal("1900")),
                new BigDecimal("11900"), companyInfo(), BigDecimal.ZERO,
                null, List.of(new ElectronicInvoiceRequest.Payment(
                        "CASH", "10", new BigDecimal("11900"), "2026-10-29")),
                "COP", null);

        var cliente = child(parse(mapper.toFacturaXml(request)).getDocumentElement(), "cliente");

        assertThat(childText(cliente, "nombreRazonSocial")).isEqualTo("Consumidor final");
        assertThat(childText(cliente, "tipoPersona")).isEqualTo("2");
        assertThat(childText(cliente, "tipoIdentificacion")).isEqualTo("13");
        assertThat(childText(cliente, "numeroDocumento")).isEqualTo("222222222222");
    }

    @Test
    void buildsSoapEnvelopeWithCredentialsAndFactura() {
        var document = parse(mapper.toEnviarEnvelope(sampleRequest()));
        var enviar = descendants(document, "Enviar").get(0);

        assertThat(enviar).isNotNull();
        assertThat(childText(enviar, "tokenEmpresa")).isEqualTo("token-empresa");
        assertThat(childText(enviar, "tokenPassword")).isEqualTo("token-password");
        var factura = child(enviar, "factura");
        assertThat(factura).isNotNull();
        var general = child(factura, "FacturaGeneral");
        assertThat(general).isNotNull();
        assertThat(childText(general, "consecutivoDocumento")).isEqualTo("1");
    }

    @Test
    void parsesHkaSuccessResponse() {
        var response = mapper.toResponse("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <EnviarResponse xmlns="http://tempuri.org/">
                      <EnviarResult xmlns:a="http://schemas.datacontract.org/2004/07/ServiceSoap.UBL2._0.Response">
                        <a:codigo>0</a:codigo>
                        <a:consecutivoDocumento>1</a:consecutivoDocumento>
                        <a:cufe>CUFE-123</a:cufe>
                        <a:esValidoDian>true</a:esValidoDian>
                        <a:mensaje>OK</a:mensaje>
                        <a:qr>https://qr.test/CUFE-123</a:qr>
                        <a:resultado>Aceptado</a:resultado>
                      </EnviarResult>
                    </EnviarResponse>
                  </soap:Body>
                </soap:Envelope>
                """);

        assertThat(response.status()).isEqualTo("ACCEPTED_BY_DIAN");
        assertThat(response.cufe()).isEqualTo("CUFE-123");
        assertThat(response.qrCode()).isEqualTo("https://qr.test/CUFE-123");
        assertThat(response.providerReference()).isEqualTo("1");
        assertThat(response.hasErrors()).isFalse();
    }

    @Test
    void parsesHkaRejectionResponseWithValidationMessages() {
        var response = mapper.toResponse("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <EnviarResponse xmlns="http://tempuri.org/">
                      <EnviarResult xmlns:a="http://schemas.datacontract.org/2004/07/ServiceSoap.UBL2._0.Response"
                                    xmlns:b="http://schemas.microsoft.com/2003/10/Serialization/Arrays">
                        <a:codigo>1</a:codigo>
                        <a:esValidoDian>false</a:esValidoDian>
                        <a:mensaje>Rechazada por la DIAN</a:mensaje>
                        <a:resultado>Rechazado</a:resultado>
                        <a:mensajesValidacion>
                          <b:string>El NIT del cliente no existe</b:string>
                          <b:string>El total no coincide</b:string>
                        </a:mensajesValidacion>
                      </EnviarResult>
                    </EnviarResponse>
                  </soap:Body>
                </soap:Envelope>
                """);

        assertThat(response.status()).isEqualTo("REJECTED_BY_DIAN");
        assertThat(response.errors()).hasSize(2);
        assertThat(response.errors()).extracting(error -> error.message())
                .containsExactly("El NIT del cliente no existe", "El total no coincide");
    }

    @Test
    void parsesSoapFaultAsProviderError() {
        var response = mapper.toResponse("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <soap:Fault>
                      <faultcode>soap:Server</faultcode>
                      <faultstring>Token inválido</faultstring>
                    </soap:Fault>
                  </soap:Body>
                </soap:Envelope>
                """);

        assertThat(response.status()).isEqualTo("REJECTED_BY_DIAN");
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().get(0).message()).contains("Token inválido");
    }

    @Test
    void parsesStatusResponse() {
        var response = mapper.toStatusResponse("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <EstadoDocumentoResponse xmlns="http://tempuri.org/">
                      <EstadoDocumentoResult xmlns:a="http://schemas.datacontract.org/2004/07/ServiceSoap.UBL2._0.Response">
                        <a:codigo>0</a:codigo>
                        <a:consecutivo>1</a:consecutivo>
                        <a:cufe>CUFE-123</a:cufe>
                        <a:esValidoDIAN>true</a:esValidoDIAN>
                        <a:mensaje>OK</a:mensaje>
                        <a:resultado>Aceptado</a:resultado>
                      </EstadoDocumentoResult>
                    </EstadoDocumentoResponse>
                  </soap:Body>
                </soap:Envelope>
                """);

        assertThat(response.status()).isEqualTo("ACCEPTED_BY_DIAN");
        assertThat(response.cufe()).isEqualTo("CUFE-123");
        assertThat(response.providerReference()).isEqualTo("1");
    }

    @Test
    void requiresNumberingRange() {
        var mapperWithoutRange = new TheFactoryInvoiceMapper(
                props(null, "token-empresa", "token-password"));

        var thrown = catchThrowable(() -> mapperWithoutRange.toFacturaXml(sampleRequest()));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo("DIAN_HKA_CONFIG_MISSING");
    }

    @Test
    void requiresAtLeastOnePayment() {
        var request = new ElectronicInvoiceRequest(
                "5", "2026-10-29", "900123456", "ACME SAS",
                List.of(new ElectronicInvoiceRequest.InvoiceItem(
                        "P5", "Producto", BigDecimal.ONE, new BigDecimal("10000"),
                        "IVA", new BigDecimal("19"), new BigDecimal("1900"),
                        new BigDecimal("10000"), BigDecimal.ZERO)),
                Map.of("19", new BigDecimal("1900")),
                new BigDecimal("11900"), companyInfo(), BigDecimal.ZERO,
                client(), List.of(), "COP", null);

        var thrown = catchThrowable(() -> mapper.toFacturaXml(request));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo("DIAN_PAYMENT_TOTAL_MISMATCH");
    }

    @Test
    void doesNotLeakCredentialsInToString() {
        assertThat(props().toString())
                .doesNotContain("token-empresa")
                .doesNotContain("token-password")
                .contains("tokenEmpresa=***");
    }

    private static FactoryProviderProperties props() {
        return props("FV-1", "token-empresa", "token-password");
    }

    private static FactoryProviderProperties props(String numberingRange, String tokenEmpresa,
                                                   String tokenPassword) {
        return new FactoryProviderProperties(
                tokenEmpresa, tokenPassword,
                "https://hka.test/Service.svc", true, 1000, 2000,
                numberingRange, "01", "05", "COP", "01", "04", "UN", "1", "00",
                "222222222222", "13", "Consumidor final");
    }

    private static ElectronicInvoiceRequest sampleRequest() {
        return new ElectronicInvoiceRequest(
                "1", "2026-10-29", "900123456", "ACME SAS",
                List.of(
                        new ElectronicInvoiceRequest.InvoiceItem(
                                "P1", "Impresora", new BigDecimal("2"), new BigDecimal("100000"),
                                "IVA", new BigDecimal("19"), new BigDecimal("38000"),
                                new BigDecimal("200000"), BigDecimal.ZERO),
                        new ElectronicInvoiceRequest.InvoiceItem(
                                "P2", "Papel", BigDecimal.ONE, new BigDecimal("50000"),
                                "IVA", new BigDecimal("5"), new BigDecimal("2500"),
                                new BigDecimal("50000"), BigDecimal.ZERO)),
                Map.of("19", new BigDecimal("38000"), "5", new BigDecimal("2500")),
                new BigDecimal("290500"), companyInfo(), BigDecimal.ZERO,
                client(), List.of(new ElectronicInvoiceRequest.Payment(
                        "CASH", "10", new BigDecimal("290500"), "2026-10-29")),
                "COP", "Venta POS 1");
    }

    private static ElectronicInvoiceRequest.CompanyInfo companyInfo() {
        return new ElectronicInvoiceRequest.CompanyInfo(
                "900987654", "POS INVENT SAS", "CL 1 2 3", "3001234567",
                "emisor@posinvent.test", "4711", "ORDINARIO", "18760000001", "12345");
    }

    private static ElectronicInvoiceRequest.ClientInfo client() {
        return new ElectronicInvoiceRequest.ClientInfo(
                "31", "900123456", "1", "Company", "ACME SAS", null,
                "CL 10 20 30", "BOGOTA", "11001", "11", "Cundinamarca", "110111",
                "CO", "O-13;O-15", "facturas@acme.test", "3009876543");
    }

    private static Document parse(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError("XML inválido: " + xml, e);
        }
    }

    private static String text(Document document, String localName) {
        var elements = descendants(document, localName);
        return elements.isEmpty() ? null : elements.get(0).getTextContent().trim();
    }

    private static Element child(Element parent, String localName) {
        if (parent == null) return null;
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && localName.equals(node.getLocalName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static String childText(Element parent, String localName) {
        var element = child(parent, localName);
        return element == null ? null : element.getTextContent().trim();
    }

    private static List<Element> descendants(Document document, String localName) {
        return descendants(document.getDocumentElement(), localName);
    }

    private static List<Element> descendants(Element parent, String localName) {
        var result = new ArrayList<Element>();
        if (parent == null) return result;
        var nodes = parent.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add((Element) nodes.item(i));
        }
        return result;
    }
}
