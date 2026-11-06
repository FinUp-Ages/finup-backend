package br.com.finup.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.finup.model.FinUpScoreInputs;
import br.com.finup.model.FinUpScoreResult;
import br.com.finup.model.IncomeExpenseRelation;
import br.com.finup.model.SpendingHabit;
import br.com.finup.model.User;
import br.com.finup.model.UserFinancialProfile;
import br.com.finup.service.FinUpScoreCalculator;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Trava que os fixtures de demonstracao reproduzem exatamente os Exemplos A/C/D de {@code
 * docs/finup-score.md} — se a formula do {@link FinUpScoreCalculator} mudar sem atualizar o
 * documento (ou vice-versa), este teste quebra.
 */
class InMemoryFinUpScoreDataProviderTest {

  private final UserFinancialProfileRepository profileRepository =
      mock(UserFinancialProfileRepository.class);
  private final InMemoryFinUpScoreDataProvider provider =
      new InMemoryFinUpScoreDataProvider(profileRepository);
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
    User user = User.createFromCognitoIdentity("mock-sub-novo", "Usuario Novo", "novo@exemplo.com");

    FinUpScoreResult result = calculator.calculate(provider.loadInputs(user));

    assertThat(result).isInstanceOf(FinUpScoreResult.InsufficientData.class);
  }

  @Test
  @DisplayName("usuario sem fixture usa a renda gravada em user_financial_profiles")
  void unknownUserUsesIncomeFromFinancialProfile() {
    User user = User.createFromCognitoIdentity("mock-sub-novo", "Usuario Novo", "novo@exemplo.com");
    UserFinancialProfile profile = UserFinancialProfile.createFor(user);
    profile.apply(null, null, null, new BigDecimal("3500.50"));
    when(profileRepository.findByUserId(user.getId())).thenReturn(Optional.of(profile));

    assertThat(provider.loadInputs(user).monthlyIncome()).isEqualByComparingTo("3500.50");
  }

  @Test
  @DisplayName(
      "usuario sem fixture usa gastos, relacao, investimento e habito do perfil da triagem")
  void unknownUserUsesTriageFieldsFromProfile() {
    User user = User.createFromCognitoIdentity("mock-sub-novo", "Usuario Novo", "novo@exemplo.com");
    UserFinancialProfile profile = UserFinancialProfile.createFor(user);
    profile.apply(null, null, null, new BigDecimal("4000"));
    profile.applyTriage(
        new BigDecimal("2500"),
        IncomeExpenseRelation.SPENDS_LESS,
        SpendingHabit.IMPULSIVE,
        true,
        new BigDecimal("12000"),
        "OVER_3_YEARS");
    when(profileRepository.findByUserId(user.getId())).thenReturn(Optional.of(profile));

    FinUpScoreInputs inputs = provider.loadInputs(user);

    assertThat(inputs.monthlyIncome()).isEqualByComparingTo("4000");
    assertThat(inputs.monthlyExpensesEstimate()).isEqualByComparingTo("2500");
    assertThat(inputs.incomeExpenseRelation()).isEqualTo(IncomeExpenseRelation.SPENDS_LESS);
    assertThat(inputs.totalInvestedAmount()).isEqualByComparingTo("12000");
    assertThat(inputs.spendingHabit()).isEqualTo(SpendingHabit.IMPULSIVE);
  }

  @Test
  @DisplayName("quem informou que nao investe tem investimento zero, mesmo com valor antigo")
  void notInvestingMeansZeroInvested() {
    User user = User.createFromCognitoIdentity("mock-sub-novo", "Usuario Novo", "novo@exemplo.com");
    UserFinancialProfile profile = UserFinancialProfile.createFor(user);
    profile.applyTriage(null, null, null, false, new BigDecimal("999"), "OVER_3_YEARS");
    when(profileRepository.findByUserId(user.getId())).thenReturn(Optional.of(profile));

    assertThat(provider.loadInputs(user).totalInvestedAmount()).isEqualByComparingTo("0");
  }

  private int scoreFor(String email, String monthlyIncome) {
    User user = User.createFromCognitoIdentity("mock-sub-" + email, "Usuario Demo", email);
    UserFinancialProfile profile = UserFinancialProfile.createFor(user);
    profile.apply(null, null, null, new BigDecimal(monthlyIncome));
    when(profileRepository.findByUserId(user.getId())).thenReturn(Optional.of(profile));
    FinUpScoreResult result = calculator.calculate(provider.loadInputs(user));
    assertThat(result).isInstanceOf(FinUpScoreResult.Computed.class);
    return ((FinUpScoreResult.Computed) result).score();
  }
}
