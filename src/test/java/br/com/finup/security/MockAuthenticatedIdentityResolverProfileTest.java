package br.com.finup.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Confirma o mecanismo por tras da exigencia "em producao, sem Cognito, a aplicacao nao deve
 * subir": o {@link MockAuthenticatedIdentityResolver} so existe como bean fora do profile "prod".
 * Como {@code UserController} depende de {@link AuthenticatedIdentityResolver} no construtor, a
 * ausencia desse bean em producao e o que derruba o contexto na inicializacao — este teste isola
 * exatamente essa condicao, sem precisar subir a aplicacao inteira (e sem depender de banco).
 */
class MockAuthenticatedIdentityResolverProfileTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(HttpServletRequest.class, () -> mock(HttpServletRequest.class))
          .withUserConfiguration(MockAuthenticatedIdentityResolver.class);

  @Test
  @DisplayName("mock de identidade existe fora do profile prod")
  void isRegisteredWhenProfileIsNotProd() {
    contextRunner
        .withPropertyValues("spring.profiles.active=dev")
        .run(context -> assertThat(context).hasSingleBean(AuthenticatedIdentityResolver.class));
  }

  @Test
  @DisplayName("mock de identidade nao existe no profile prod")
  void isAbsentWhenProfileIsProd() {
    contextRunner
        .withPropertyValues("spring.profiles.active=prod")
        .run(context -> assertThat(context).doesNotHaveBean(AuthenticatedIdentityResolver.class));
  }
}
