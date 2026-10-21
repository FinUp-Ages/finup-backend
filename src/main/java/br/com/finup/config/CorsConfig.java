package br.com.finup.config;

import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS para o painel web (Vite) e para o app mobile em desenvolvimento.
 *
 * <p>As origens vem de {@code CORS_ALLOWED_ORIGINS}, separadas por virgula — nunca fixe host de
 * producao em codigo. O default cobre o Vite local. {@code *} reprova a inicializacao, em qualquer
 * ambiente: so as origens conhecidas podem ler as respostas da API pelo navegador. Antes o curinga
 * so falhava na primeira requisicao de navegador, porque o Spring nao o aceita junto de
 * credenciais.
 *
 * <p>Os headers aceitos sao so os que os clientes mandam: o token, o tipo do corpo e o {@code
 * Accept}. Com o profile {@code mock-auth} entram tambem os {@code X-Mock-Cognito-*}, para o
 * navegador conseguir simular a identidade em desenvolvimento.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  private static final String[] ALLOWED_METHODS = {
    "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
  };
  private static final String[] ALLOWED_HEADERS = {
    HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT
  };
  private static final String[] MOCK_AUTH_HEADERS = {
    "X-Mock-Cognito-Sub", "X-Mock-Cognito-Email", "X-Mock-Cognito-Name"
  };

  private final String[] allowedOrigins;
  private final String[] allowedHeaders;

  public CorsConfig(
      @Value("${finup.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins,
      Environment environment) {
    if (Arrays.asList(allowedOrigins).contains(CorsConfiguration.ALL)) {
      throw new IllegalStateException(
          "finup.cors.allowed-origins (CORS_ALLOWED_ORIGINS) nao aceita \"*\":"
              + " liste as origens do painel e do app.");
    }
    this.allowedOrigins = allowedOrigins;
    this.allowedHeaders =
        environment.matchesProfiles("mock-auth")
            ? Stream.of(ALLOWED_HEADERS, MOCK_AUTH_HEADERS)
                .flatMap(Arrays::stream)
                .toArray(String[]::new)
            : ALLOWED_HEADERS;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods(ALLOWED_METHODS)
        .allowedHeaders(allowedHeaders)
        .allowCredentials(true)
        .maxAge(3600);
  }
}
