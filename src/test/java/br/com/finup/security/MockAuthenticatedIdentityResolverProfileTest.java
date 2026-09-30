package br.com.finup.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * O {@link MockAuthenticatedIdentityResolver} so existe com o profile "mock-auth", e nunca em
 * "prod". Como os controllers dependem de {@link AuthenticatedIdentityResolver} no construtor, e o
 * resolver do Cognito e {@code !mock-auth}, ativar o mock em producao deixa a aplicacao sem nenhum
 * resolver e derruba o contexto na inicializacao — este teste isola essa condicao, sem subir a
 * aplicacao inteira (e sem depender de banco).
 */
class MockAuthenticatedIdentityResolverProfileTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(HttpServletRequest.class, () -> mock(HttpServletRequest.class))
          .withUserConfiguration(MockAuthenticatedIdentityResolver.class);

  @Test
  @DisplayName("mock de identidade existe com o profile mock-auth")
  void isRegisteredWithMockAuthProfile() {
    contextRunner
        .withPropertyValues("spring.profiles.active=dev,mock-auth")
        .run(context -> assertThat(context).hasSingleBean(AuthenticatedIdentityResolver.class));
  }

  @Test
  @DisplayName("mock de identidade nao existe sem o profile mock-auth")
  void isAbsentWithoutMockAuthProfile() {
    contextRunner
        .withPropertyValues("spring.profiles.active=dev")
        .run(context -> assertThat(context).doesNotHaveBean(AuthenticatedIdentityResolver.class));
  }

  @Test
  @DisplayName("mock de identidade nao existe em prod, nem com o profile mock-auth")
  void isAbsentInProdEvenWithMockAuthProfile() {
    contextRunner
        .withPropertyValues("spring.profiles.active=prod,mock-auth")
        .run(context -> assertThat(context).doesNotHaveBean(AuthenticatedIdentityResolver.class));
  }
}
