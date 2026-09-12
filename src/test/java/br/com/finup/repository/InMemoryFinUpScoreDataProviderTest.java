package br.com.finup.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finup.model.FinUpScoreResult;
import br.com.finup.model.User;
import br.com.finup.service.FinUpScoreCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Trava que os fixtures de demonstracao reproduzem exatamente os Exemplos A/C/D de {@code
 * docs/finup-score.md} — se a formula do {@link FinUpScoreCalculator} mudar sem atualizar o
 * documento (ou vice-versa), este teste quebra.
 */
class InMemoryFinUpScoreDataProviderTest {

  private final InMemoryFinUpScoreDataProvider provider = new InMemoryFinUpScoreDataProvider();
  private final FinUpScoreCalculator calculator = new FinUpScoreCalculator();

  @Test
  @DisplayName("fixture do Exemplo A (fallback de estimativa) produz score 696")
  void exampleAFixtureProducesDocumentedScore() {
    assertThat(scoreFor(InMemoryFinUpScoreDataProvider.DEMO_FALLBACK_ESTIMATE_EMAIL, "5000"))
        .isEqualTo(696);
  }

  @Test
  @DisplayName("fixture do Exemplo C (usuario saudavel) produz score 950")
  void exampleCFixtureProducesDocumentedScore() {
    assertThat(scoreFor(InMemoryFinUpScoreDataProvider.DEMO_HEALTHY_EMAIL, "8000")).isEqualTo(950);
  }

  @Test
  @DisplayName("fixture do Exemplo D (usuario em dificuldade) produz score 106")
  void exampleDFixtureProducesDocumentedScore() {
    assertThat(scoreFor(InMemoryFinUpScoreDataProvider.DEMO_STRUGGLING_EMAIL, "3000"))
        .isEqualTo(106);
  }

  @Test
  @DisplayName("usuario sem fixture e sem renda cadastrada resulta em dado insuficiente")
  void unknownUserWithoutIncomeIsInsufficientData() {
    User user = User.register("Usuario Novo", "novo@exemplo.com");

    FinUpScoreResult result = calculator.calculate(provider.loadInputs(user));

    assertThat(result).isInstanceOf(FinUpScoreResult.InsufficientData.class);
  }

  private int scoreFor(String email, String monthlyIncome) {
    User user =
        User.register("Usuario Demo", email).withMonthlyIncome(new BigDecimal(monthlyIncome));
    FinUpScoreResult result = calculator.calculate(provider.loadInputs(user));
    assertThat(result).isInstanceOf(FinUpScoreResult.Computed.class);
    return ((FinUpScoreResult.Computed) result).score();
  }
}
