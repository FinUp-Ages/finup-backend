package br.com.finup.model;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Respostas da triagem já validadas e tipadas. Só existe depois de o validador aprovar o envio, e
 * por isso os campos condicionais ({@code debt*}, {@code invest*}) são nulos quando a pergunta que
 * os liga respondeu "não".
 */
public record TriageAnswers(
    IncomeType incomeType,
    int childrenCount,
    int dependentsCount,
    IncomeExpenseRelation incomeExpenseRelation,
    BigDecimal monthlyExpenses,
    SpendingHabit spendingHabit,
    boolean hasDebt,
    Set<DebtType> debtTypes,
    BigDecimal debtTotalAmount,
    boolean latePayments,
    EmergencyReserve emergencyReserve,
    boolean invests,
    BigDecimal investedAmount,
    String investmentTime,
    Set<String> investmentInterests,
    String mainGoal,
    MarketDropReaction marketDropReaction,
    Set<RiskyHabit> riskyHabits) {

  public enum IncomeType {
    FIXED,
    VARIABLE,
    MIXED
  }

  public enum DebtType {
    CREDIT_CARD,
    OVERDRAFT,
    PERSONAL_LOAN,
    FINANCING,
    OTHER
  }

  public enum EmergencyReserve {
    NONE,
    UP_TO_3_MONTHS,
    FROM_3_TO_6_MONTHS,
    OVER_6_MONTHS
  }

  /** Reação a uma queda de 20% num investimento: define o perfil de investidor. */
  public enum MarketDropReaction {
    SELL_ALL(FinancialProfile.CONSERVATIVE),
    WAIT(FinancialProfile.MODERATE),
    BUY_MORE(FinancialProfile.AGGRESSIVE);

    private final FinancialProfile profile;

    MarketDropReaction(FinancialProfile profile) {
      this.profile = profile;
    }

    public FinancialProfile profile() {
      return profile;
    }
  }

  public enum RiskyHabit {
    BETS,
    ALCOHOL,
    TOBACCO
  }
}
