package co.posinvent.infrastructure.adapters.out.dian.thefactory;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.infrastructure.config.FactoryProviderProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link TheFactoryClient}. They assert the SOAP 1.1 action header and that a
 * SOAP Fault body is preserved for the mapper instead of being swallowed by the HTTP layer.
 */
class TheFactoryClientTest {

    private static final String BASE_URL = "https://hka.test/Service.svc";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private TheFactoryClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        client = new TheFactoryClient(builder, props());
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void postsEnviarEnvelopeWithSoap11Action() {
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("SOAPAction", "\"http://tempuri.org/IService/Enviar\""))
                .andExpect(content().string(containsString("<Enviar")))
                .andRespond(withSuccess(responseXml("EnviarResult"), MediaType.TEXT_XML));

        var body = client.enviar("<Enviar/>");

        assertThat(body).contains("EnviarResult");
        server.verify();
    }

    @Test
    void postsEstadoDocumentoWithItsOwnAction() {
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("SOAPAction", "\"http://tempuri.org/IService/EstadoDocumento\""))
                .andExpect(content().string(containsString("<EstadoDocumento")))
                .andRespond(withSuccess(responseXml("EstadoDocumentoResult"), MediaType.TEXT_XML));

        var body = client.estadoDocumento("<EstadoDocumento/>");

        assertThat(body).contains("EstadoDocumentoResult");
        server.verify();
    }

    @Test
    void returnsSoapFaultBodyOnServerErrorSoTheMapperCanExplainIt() {
        var fault = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <soap:Fault>
                      <faultstring>Token inválido</faultstring>
                    </soap:Fault>
                  </soap:Body>
                </soap:Envelope>
                """;
        server.expect(requestTo(BASE_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.TEXT_XML)
                        .body(fault));

        var body = client.enviar("<Enviar/>");

        assertThat(body).contains("faultstring");
        server.verify();
    }

    @Test
    void wrapsNonXmlServerErrorsInAProviderError() {
        server.expect(requestTo(BASE_URL))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("gateway down"));

        var thrown = catchThrowable(() -> client.enviar("<Enviar/>"));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo("DIAN_PROVIDER_ERROR");
        assertThat(thrown).hasMessageContaining("The Factory HKA");
        server.verify();
    }

    private static String responseXml(String resultName) {
        return """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <Response xmlns="http://tempuri.org/">
                      <%s>ok</%s>
                    </Response>
                  </soap:Body>
                </soap:Envelope>
                """.formatted(resultName, resultName);
    }

    private static FactoryProviderProperties props() {
        return new FactoryProviderProperties(
                "token-empresa", "token-password",
                BASE_URL, true, 1000, 2000,
                "FV-1", "01", "05", "COP", "01", "04", "UN", "1", "00",
                "222222222222", "13", "Consumidor final");
    }
}
