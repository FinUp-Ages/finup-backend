package br.com.finup.model;

import java.math.BigDecimal;

/**
 * Dados agregados que alimentam o {@code FinUpScoreCalculator}. Fronteira entre a busca de dados
 * (hoje mockada, ver {@code FinUpScoreDataProvider}) e o calculo em si.
 *
 * <p>Semantica de cada campo esta em {@code docs/finup-score.md}. Regra geral: {@code null} so
 * quando o dado genuinamente nao existe; ausencia de divida/investimento e zero, nao {@code null}.
 */
public record FinUpScoreInputs(
    BigDecimal monthlyIncome,
    BigDecimal monthlyExpensesFromTransactions,
    BigDecimal monthlyExpensesEstimate,
    IncomeExpenseRelation incomeExpenseRelation,
    BigDecimal totalActiveDebtBalance,
    boolean hasLateDebt,
    BigDecimal totalInvestedAmount,
    Double goalsProgressRatio,
    SpendingHabit spendingHabit) {}
