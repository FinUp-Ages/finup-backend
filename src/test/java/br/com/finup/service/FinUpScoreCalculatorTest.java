package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finup.model.FinUpScoreInputs;
import br.com.finup.model.FinUpScoreResult;
import br.com.finup.model.IncomeExpenseRelation;
import br.com.finup.model.SpendingHabit;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Casos principais espelham, número a número, os Exemplos A a D de {@code docs/finup-score.md} —
 * qualquer mudança de fórmula que quebre estes testes exige atualizar o documento junto.
 */
class FinUpScoreCalculatorTest {

  private final FinUpScoreCalculator calculator = new FinUpScoreCalculator();

  @Test
  @DisplayName("Exemplo A: fallback para estimativa de gastos produz score 70")
  void exampleAFallbackEstimate() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("5000"),
            null,
            new BigDecimal("3000"),
            IncomeExpenseRelation.BALANCED,
            new BigDecimal("2000"),
            false,
            new BigDecimal("2150"),
            0.30,
            SpendingHabit.MODERATE);

    assertThat(computedScore(inputs)).isEqualTo(70);
  }

  @Test
  @DisplayName("Exemplo B: sem renda mensal, resultado e dado insuficiente")
  void exampleBInsufficientData() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            null, null, null, null, BigDecimal.ZERO, false, BigDecimal.ZERO, null, null);

    assertThat(calculator.calculate(inputs)).isInstanceOf(FinUpScoreResult.InsufficientData.class);
  }

  @Test
  @DisplayName("Exemplo C: usuario saudavel satura os tetos e produz score 95")
  void exampleCHealthyUser() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("8000"),
            null,
            new BigDecimal("5000"),
            null,
            BigDecimal.ZERO,
            false,
            new BigDecimal("50000"),
            null,
            SpendingHabit.CONTROLLED);

    assertThat(computedScore(inputs)).isEqualTo(95);
  }

  @Test
  @DisplayName("Exemplo D: dividas em atraso e savings negativo produzem score 11")
  void exampleDStrugglingUser() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("3000"),
            null,
            new BigDecimal("3900"),
            null,
            new BigDecimal("20000"),
            true,
            BigDecimal.ZERO,
            null,
            SpendingHabit.IMPULSIVE);

    assertThat(computedScore(inputs)).isEqualTo(11);
  }

  @Test
  @DisplayName("renda zero e tratada como dado insuficiente")
  void zeroIncomeIsInsufficientData() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            BigDecimal.ZERO, null, null, null, BigDecimal.ZERO, false, BigDecimal.ZERO, null, null);

    assertThat(calculator.calculate(inputs)).isInstanceOf(FinUpScoreResult.InsufficientData.class);
  }

  @Test
  @DisplayName("renda negativa e tratada como dado insuficiente")
  void negativeIncomeIsInsufficientData() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("-100"),
            null,
            null,
            null,
            BigDecimal.ZERO,
            false,
            BigDecimal.ZERO,
            null,
            null);

    assertThat(calculator.calculate(inputs)).isInstanceOf(FinUpScoreResult.InsufficientData.class);
  }

  @Test
  @DisplayName("taxa de poupanca acima de 30% satura o pilar de orcamento em 35")
  void savingsRateAboveCapSaturatesBudgetPillar() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("10000"),
            new BigDecimal("1000"),
            null,
            null,
            BigDecimal.ZERO,
            false,
            BigDecimal.ZERO,
            null,
            null);

    // pilar1=35 + pilar2=25 + pilar3=0 + pilar4=5(neutro) + pilar5=6(neutro) = 71
    assertThat(computedScore(inputs)).isEqualTo(71);
  }

  @Test
  @DisplayName("dividas que somam a renda anual inteira zeram o pilar de endividamento")
  void debtEqualToAnnualIncomeZeroesDebtPillar() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("1000"),
            null,
            new BigDecimal("1000"),
            null,
            new BigDecimal("12000"),
            false,
            BigDecimal.ZERO,
            null,
            null);

    // pilar1=17,5(savingsRate 0) + pilar2=0 + pilar3=0 + pilar4=5 + pilar5=6 = 28,5 -> 29
    assertThat(computedScore(inputs)).isEqualTo(29);
  }

  @Test
  @DisplayName("divida em atraso subtrai 7,5 pontos quando a base comporta a penalidade")
  void lateDebtAppliesFixedPenalty() {
    FinUpScoreInputs withoutLate =
        new FinUpScoreInputs(
            new BigDecimal("1000"),
            null,
            new BigDecimal("1000"),
            null,
            new BigDecimal("6000"),
            false,
            BigDecimal.ZERO,
            null,
            null);
    FinUpScoreInputs withLate =
        new FinUpScoreInputs(
            new BigDecimal("1000"),
            null,
            new BigDecimal("1000"),
            null,
            new BigDecimal("6000"),
            true,
            BigDecimal.ZERO,
            null,
            null);

    // ratio=0,5 -> pilar2=12,5 sem atraso e 5 com atraso.
    // Sem atraso: 17,5 + 12,5 + 0 + 5 + 6 = 41. Com atraso: 17,5 + 5 + 0 + 5 + 6 = 33,5 -> 34.
    assertThat(computedScore(withoutLate)).isEqualTo(41);
    assertThat(computedScore(withLate)).isEqualTo(34);
  }

  @Test
  @DisplayName("penalidade de atraso tem piso zero e nao deixa o pilar de endividamento negativo")
  void lateDebtPenaltyFloorsAtZero() {
    FinUpScoreInputs withLate =
        new FinUpScoreInputs(
            new BigDecimal("1000"),
            null,
            new BigDecimal("1000"),
            null,
            new BigDecimal("10800"),
            true,
            BigDecimal.ZERO,
            null,
            null);

    // ratio=0,9 -> base=2,5 (sem atraso); com atraso, 2,5-7,5 seria negativo, mas o piso e zero.
    // pilar1=17,5 + pilar2=0 + pilar3=0 + pilar4=5 + pilar5=6 = 28,5 -> 29
    assertThat(computedScore(withLate)).isEqualTo(29);
  }

  @Test
  @DisplayName("6 meses de renda investida satura o pilar de reserva em 20")
  void sixMonthsOfIncomeInvestedSaturatesInvestmentPillar() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("1000"),
            null,
            new BigDecimal("1000"),
            null,
            BigDecimal.ZERO,
            false,
            new BigDecimal("6000"),
            null,
            null);

    // pilar1=17,5 + pilar2=25 + pilar3=20 + pilar4=5 + pilar5=6 = 73,5 -> 74
    assertThat(computedScore(inputs)).isEqualTo(74);
  }

  @Test
  @DisplayName("meta concluida conta progresso 1 e meta cancelada e ignorada")
  void completedGoalCountsFullProgressAndCancelledIsIgnored() {
    FinUpScoreInputs inputs =
        new FinUpScoreInputs(
            new BigDecimal("1000"),
            null,
            new BigDecimal("1000"),
            null,
            BigDecimal.ZERO,
            false,
            BigDecimal.ZERO,
            1.0,
            null);

    // pilar1=17,5 + pilar2=25 + pilar3=0 + pilar4=10 + pilar5=6 = 58,5 -> 59
    assertThat(computedScore(inputs)).isEqualTo(59);
  }

  private int computedScore(FinUpScoreInputs inputs) {
    FinUpScoreResult result = calculator.calculate(inputs);
    assertThat(result).isInstanceOf(FinUpScoreResult.Computed.class);
    return ((FinUpScoreResult.Computed) result).score();
  }
}
