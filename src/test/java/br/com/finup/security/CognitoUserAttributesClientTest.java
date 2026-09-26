package br.com.finup.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.finup.exception.IdentityProviderUnavailableException;
import br.com.finup.exception.MissingAuthenticatedIdentityException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Contrato com a API {@code GetUser} do Cognito, com o servidor HTTP simulado. O formato da
 * resposta segue a documentacao da AWS: {@code UserAttributes} e uma lista de pares {@code
 * Name}/{@code Value}.
 */
class CognitoUserAttributesClientTest {

  private static final String ENDPOINT = "https://cognito-idp.us-east-1.amazonaws.com";
  private static final MediaType AMZ_JSON = MediaType.parseMediaType("application/x-amz-json-1.1");

  private MockRestServiceServer server;
  private CognitoUserAttributesClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(ENDPOINT);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new CognitoUserAttributesClient(builder.build(), new ObjectMapper());
  }

  @AfterEach
  void verifyServer() {
    server.verify();
  }

  @Test
  @DisplayName("chama o GetUser com o access token e le sub, e-mail e nome")
  void fetchesAttributes() {
    server
        .expect(requestTo(ENDPOINT + "/"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Amz-Target", "AWSCognitoIdentityProviderService.GetUser"))
        .andExpect(header("Content-Type", startsWith("application/x-amz-json-1.1")))
        .andExpect(content().json("{\"AccessToken\":\"token-abc\"}"))
        .andRespond(
            withSuccess(
                """
                {"Username": "ana",
                 "UserAttributes": [
                   {"Name": "sub", "Value": "cognito-sub-123"},
                   {"Name": "email_verified", "Value": "true"},
                   {"Name": "email", "Value": "ana@exemplo.com"},
                   {"Name": "name", "Value": "João Souza"}
                 ]}
                """,
                AMZ_JSON));

    CognitoUserAttributesClient.UserAttributes attributes = client.fetch("token-abc");

    assertThat(attributes.sub()).isEqualTo("cognito-sub-123");
    assertThat(attributes.email()).isEqualTo("ana@exemplo.com");
    assertThat(attributes.name()).isEqualTo("João Souza");
  }

  @Test
  @DisplayName("usuario sem o atributo name devolve nome nulo")
  void fetchesAttributesWithoutName() {
    server
        .expect(requestTo(ENDPOINT + "/"))
        .andRespond(
            withSuccess(
                """
                {"UserAttributes": [
                  {"Name": "sub", "Value": "cognito-sub-123"},
                  {"Name": "email", "Value": "ana@exemplo.com"}
                ]}
                """,
                AMZ_JSON));

    assertThat(client.fetch("token-abc").name()).isNull();
  }

  @Test
  @DisplayName("token recusado pelo Cognito vira 401")
  void rejectedTokenBecomesUnauthorized() {
    server
        .expect(requestTo(ENDPOINT + "/"))
        .andRespond(
            withBadRequest()
                .contentType(AMZ_JSON)
                .body(
                    "{\"__type\":\"NotAuthorizedException\",\"message\":\"Access Token has been"
                        + " revoked\"}"));

    assertThatThrownBy(() -> client.fetch("token-abc"))
        .isInstanceOf(MissingAuthenticatedIdentityException.class);
  }

  @Test
  @DisplayName("limite de requisicoes do Cognito vira 503, e nao 401")
  void throttlingBecomesUnavailable() {
    server.expect(requestTo(ENDPOINT + "/")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThatThrownBy(() -> client.fetch("token-abc"))
        .isInstanceOf(IdentityProviderUnavailableException.class);
  }

  @Test
  @DisplayName("erro do lado do Cognito vira 503")
  void serverErrorBecomesUnavailable() {
    server.expect(requestTo(ENDPOINT + "/")).andRespond(withServerError());

    assertThatThrownBy(() -> client.fetch("token-abc"))
        .isInstanceOf(IdentityProviderUnavailableException.class);
  }

  @Test
  @DisplayName("resposta que nao e JSON vira 503")
  void malformedResponseBecomesUnavailable() {
    server.expect(requestTo(ENDPOINT + "/")).andRespond(withSuccess("nao e json", AMZ_JSON));

    assertThatThrownBy(() -> client.fetch("token-abc"))
        .isInstanceOf(IdentityProviderUnavailableException.class);
  }
}
