package br.com.finup.security;

import br.com.finup.config.CognitoProperties;
import br.com.finup.exception.IdentityProviderUnavailableException;
import br.com.finup.exception.MissingAuthenticatedIdentityException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Busca os atributos (e-mail, nome) do usuario dono de um access token, via {@code GetUser} do
 * Cognito.
 *
 * <p>Existe porque o access token do Cognito so traz o {@code sub}: e-mail e nome ficam no ID
 * token, que nao e credencial de acesso a API. O {@code GetUser} e autorizado pelo proprio access
 * token do usuario — o backend nao precisa de credencial da AWS nem do SDK.
 *
 * <p>E uma chamada de rede por requisicao, entao so o cadastro ({@code POST /api/v1/users}) usa. O
 * resto da API se contenta com o {@code sub}, que vem do token ja validado.
 */
@Component
@Profile("!mock-auth")
public class CognitoUserAttributesClient {

  private static final Logger log = LoggerFactory.getLogger(CognitoUserAttributesClient.class);
  private static final MediaType AMZ_JSON = MediaType.parseMediaType("application/x-amz-json-1.1");
  private static final String GET_USER_TARGET = "AWSCognitoIdentityProviderService.GetUser";

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  @Autowired
  public CognitoUserAttributesClient(
      RestClient.Builder restClientBuilder, CognitoProperties cognito, ObjectMapper objectMapper) {
    this(
        restClientBuilder.baseUrl(cognito.apiEndpoint()).requestFactory(timeouts()).build(),
        objectMapper);
  }

  CognitoUserAttributesClient(RestClient restClient, ObjectMapper objectMapper) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
  }

  /**
   * @throws MissingAuthenticatedIdentityException se o Cognito recusar o token (revogado por logout
   *     global, usuario desabilitado ou apagado do pool)
   * @throws IdentityProviderUnavailableException se o Cognito nao responder
   */
  public UserAttributes fetch(String accessToken) {
    byte[] response;
    try {
      response =
          restClient
              .post()
              .uri("/")
              .contentType(AMZ_JSON)
              .header("X-Amz-Target", GET_USER_TARGET)
              .body(objectMapper.writeValueAsBytes(Map.of("AccessToken", accessToken)))
              .retrieve()
              .body(byte[].class);
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
        log.warn("Cognito limitou o GetUser (429)");
        throw new IdentityProviderUnavailableException();
      }
      log.warn("Cognito recusou o GetUser: status={}", e.getStatusCode());
      throw new MissingAuthenticatedIdentityException();
    } catch (RestClientException | JsonProcessingException e) {
      log.error("Falha ao consultar o GetUser do Cognito", e);
      throw new IdentityProviderUnavailableException();
    }
    return parse(response);
  }

  /**
   * Le bytes, e nao {@code String}: o Cognito responde sem charset no {@code Content-Type}, e o
   * conversor de {@code String} cairia em ISO-8859-1, estragando nomes acentuados. O Jackson
   * detecta o UTF-8 sozinho.
   */
  private UserAttributes parse(byte[] response) {
    try {
      GetUserResponse body = objectMapper.readValue(response, GetUserResponse.class);
      List<Attribute> attributes = Objects.requireNonNullElse(body.userAttributes(), List.of());
      return new UserAttributes(
          valueOf(attributes, "sub"), valueOf(attributes, "email"), valueOf(attributes, "name"));
    } catch (IOException | IllegalArgumentException e) {
      log.error("Resposta inesperada do GetUser do Cognito", e);
      throw new IdentityProviderUnavailableException();
    }
  }

  private static String valueOf(List<Attribute> attributes, String name) {
    return attributes.stream()
        .filter(attribute -> name.equals(attribute.name()))
        .map(Attribute::value)
        .findFirst()
        .orElse(null);
  }

  private static SimpleClientHttpRequestFactory timeouts() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofSeconds(5));
    return factory;
  }

  /** Atributos do usuario no Cognito. {@code name} pode vir nulo; os outros, em tese, nunca. */
  public record UserAttributes(String sub, String email, String name) {}

  /** A resposta tem outros campos ({@code Username}, {@code MFAOptions}...) que nao interessam. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record GetUserResponse(@JsonProperty("UserAttributes") List<Attribute> userAttributes) {}

  private record Attribute(
      @JsonProperty("Name") String name, @JsonProperty("Value") String value) {}
}
