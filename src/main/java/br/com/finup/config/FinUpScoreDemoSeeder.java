package br.com.finup.config;

import br.com.finup.model.User;
import br.com.finup.repository.InMemoryFinUpScoreDataProvider;
import br.com.finup.repository.UserRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Scaffolding temporario de demonstracao: cadastra, na subida da aplicacao, os 3 usuarios cujos
 * e-mails o {@link InMemoryFinUpScoreDataProvider} reconhece como fixtures dos Exemplos A/C/D de
 * {@code docs/finup-score.md}.
 *
 * <p>Existe porque ainda nao ha fluxo de onboarding/diagnostico financeiro (outra tarefa) que
 * permita informar renda via API — sem isso nao daria para exercitar {@code POST
 * /api/v1/users/{id}/finup-score/recalculate} manualmente e ver o Score calculado batendo com o
 * documento. Nao e dado de negocio real, so um atalho de desenvolvimento.
 *
 * <p><strong>Remover quando existir onboarding real:</strong> quando houver um jeito de um usuario
 * informar a propria renda pela API, esta classe perde a razao de existir.
 */
@Component
public class FinUpScoreDemoSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(FinUpScoreDemoSeeder.class);

  private final UserRepository userRepository;

  public FinUpScoreDemoSeeder(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public void run(ApplicationArguments args) {
    User a =
        seed(
            "Exemplo A - Fallback de estimativa",
            InMemoryFinUpScoreDataProvider.DEMO_FALLBACK_ESTIMATE_EMAIL,
            "5000");
    User c =
        seed(
            "Exemplo C - Usuario saudavel",
            InMemoryFinUpScoreDataProvider.DEMO_HEALTHY_EMAIL,
            "8000");
    User d =
        seed(
            "Exemplo D - Usuario em dificuldade",
            InMemoryFinUpScoreDataProvider.DEMO_STRUGGLING_EMAIL,
            "3000");

    log.info(
        "Usuarios de demonstracao do FinUp Score prontos: A={} (score esperado 696), C={} (score"
            + " esperado 950), D={} (score esperado 106). Chame POST"
            + " /api/v1/users/{{id}}/finup-score/recalculate com cada id.",
        a.getId(),
        c.getId(),
        d.getId());
  }

  private User seed(String name, String email, String monthlyIncome) {
    if (userRepository.existsByEmail(email)) {
      return userRepository.findAll().stream()
          .filter(user -> user.getEmail().equalsIgnoreCase(email))
          .findFirst()
          .orElseThrow();
    }
    User user = User.register(name, email).withMonthlyIncome(new BigDecimal(monthlyIncome));
    return userRepository.save(user);
  }
}
