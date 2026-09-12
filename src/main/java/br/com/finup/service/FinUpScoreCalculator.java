package br.com.finup.service;

import br.com.finup.model.FinUpScoreInputs;
import br.com.finup.model.FinUpScoreResult;
import br.com.finup.model.IncomeExpenseRelation;
import br.com.finup.model.SpendingHabit;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Regra de calculo do FinUp Score, definida em {@code docs/finup-score.md}. Mudou uma constante
 * aqui, atualize o documento junto.
 *
 * <p>Puro de proposito: recebe {@link FinUpScoreInputs} ja agregado, sem I/O, para poder ser
 * testado com exemplos numericos simples.
 */
@Component
public class FinUpScoreCalculator {

  private static final double BUDGET_WEIGHT = 350;
  private static final double DEBT_WEIGHT = 250;
  private static final double INVESTMENT_WEIGHT = 200;
  private static final double GOALS_WEIGHT = 100;
  private static final double HABIT_WEIGHT = 100;

  private static final double SAVINGS_RATE_CLAMP = 0.30;
  private static final double LATE_DEBT_PENALTY = 75;
  private static final double EMERGENCY_FUND_MONTHS = 6;

  private static final double GOALS_NEUTRAL = GOALS_WEIGHT / 2;
  private static final double HABIT_NEUTRAL = HABIT_WEIGHT * 0.6;

  /** Sem {@code monthlyIncome > 0} nenhum pilar tem base de calculo. */
  public FinUpScoreResult calculate(FinUpScoreInputs inputs) {
    BigDecimal income = inputs.monthlyIncome();
    if (income == null || income.signum() <= 0) {
      return new FinUpScoreResult.InsufficientData(
          "Renda mensal (Users.MonthlyIncome) nao informada ou invalida.");
    }
    double monthlyIncome = income.doubleValue();

    double total =
        budgetScore(monthlyIncome, inputs)
            + debtScore(monthlyIncome, inputs)
            + investmentScore(monthlyIncome, inputs)
            + goalsScore(inputs)
            + habitScore(inputs);

    int score = (int) Math.round(clamp(total, 0, 1000));
    return new FinUpScoreResult.Computed(score);
  }

  private double budgetScore(double monthlyIncome, FinUpScoreInputs inputs) {
    Double savingsRate = savingsRate(monthlyIncome, inputs);
    if (savingsRate == null) {
      return BUDGET_WEIGHT / 2;
    }
    double clamped = clamp(savingsRate, -SAVINGS_RATE_CLAMP, SAVINGS_RATE_CLAMP);
    return BUDGET_WEIGHT * (clamped + SAVINGS_RATE_CLAMP) / (2 * SAVINGS_RATE_CLAMP);
  }

  // Ordem de fallback para "gastos": transacoes reais > estimativa do perfil > relacao declarada.
  private Double savingsRate(double monthlyIncome, FinUpScoreInputs inputs) {
    if (inputs.monthlyExpensesFromTransactions() != null) {
      return (monthlyIncome - inputs.monthlyExpensesFromTransactions().doubleValue())
          / monthlyIncome;
    }
    if (inputs.monthlyExpensesEstimate() != null) {
      return (monthlyIncome - inputs.monthlyExpensesEstimate().doubleValue()) / monthlyIncome;
    }
    if (inputs.incomeExpenseRelation() != null) {
      return assumedSavingsRate(inputs.incomeExpenseRelation());
    }
    return null;
  }

  private double assumedSavingsRate(IncomeExpenseRelation relation) {
    return switch (relation) {
      case SPENDS_LESS -> 0.25;
      case BALANCED -> 0.0;
      case SPENDS_MORE -> -0.25;
    };
  }

  private double debtScore(double monthlyIncome, FinUpScoreInputs inputs) {
    double annualIncome = monthlyIncome * 12;
    double ratio = clamp(inputs.totalActiveDebtBalance().doubleValue() / annualIncome, 0, 1);
    double base = DEBT_WEIGHT * (1 - ratio);
    if (inputs.hasLateDebt()) {
      base = Math.max(0, base - LATE_DEBT_PENALTY);
    }
    return base;
  }

  private double investmentScore(double monthlyIncome, FinUpScoreInputs inputs) {
    double monthsCovered = inputs.totalInvestedAmount().doubleValue() / monthlyIncome;
    return INVESTMENT_WEIGHT * clamp(monthsCovered / EMERGENCY_FUND_MONTHS, 0, 1);
  }

  private double goalsScore(FinUpScoreInputs inputs) {
    if (inputs.goalsProgressRatio() == null) {
      return GOALS_NEUTRAL;
    }
    return GOALS_WEIGHT * clamp(inputs.goalsProgressRatio(), 0, 1);
  }

  private double habitScore(FinUpScoreInputs inputs) {
    SpendingHabit habit = inputs.spendingHabit();
    if (habit == null) {
      return HABIT_NEUTRAL;
    }
    return switch (habit) {
      case CONTROLLED -> HABIT_WEIGHT;
      case MODERATE -> HABIT_NEUTRAL;
      case IMPULSIVE -> HABIT_WEIGHT * 0.2;
    };
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
