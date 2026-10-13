package br.com.finup.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * O que a lista de origens do CORS aceita. O comportamento do preflight em si (origem liberada,
 * origem recusada, headers) esta no {@code AdminAuthIntegrationTests}.
 */
class CorsConfigTest {

  @Test
  @DisplayName("origens explicitas sao aceitas")
  void acceptsExplicitOrigins() {
    assertThatCode(
            () ->
                new CorsConfig(
                    new String[] {"https://painel.finup.com.br", "http://localhost:5173"},
                    new MockEnvironment()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("\"*\" nas origens impede a aplicacao de subir, e a mensagem diz qual variavel")
  void rejectsWildcardOrigin() {
    assertThatThrownBy(
            () ->
                new CorsConfig(
                    new String[] {"https://painel.finup.com.br", "*"}, new MockEnvironment()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CORS_ALLOWED_ORIGINS");
  }

  @Test
  @DisplayName("\"*\" e recusado tambem com o profile prod ativo")
  void rejectsWildcardOriginInProd() {
    MockEnvironment prod = new MockEnvironment();
    prod.setActiveProfiles("prod");

    assertThatThrownBy(() -> new CorsConfig(new String[] {"*"}, prod))
        .isInstanceOf(IllegalStateException.class);
  }
}
