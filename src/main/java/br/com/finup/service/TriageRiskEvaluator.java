package br.com.finup.service;

import br.com.finup.model.FinancialProfile;
import br.com.finup.model.IncomeExpenseRelation;
import br.com.finup.model.RiskSeverity;
import br.com.finup.model.SpendingHabit;
import br.com.finup.model.TriageAnswers;
import br.com.finup.model.TriageAnswers.DebtType;
import br.com.finup.model.TriageAnswers.EmergencyReserve;
import br.com.finup.model.TriageAnswers.IncomeType;
import br.com.finup.model.TriageAnswers.MarketDropReaction;
import br.com.finup.model.TriageAnswers.RiskyHabit;
import br.com.finup.model.TriageRisk;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Regras de risco da triagem, v1: fixas, sem IA. Cada regra lê as respostas e devolve zero ou um
 * {@link TriageRisk}; os limiares são as constantes abaixo, para mudar um sem caçar número no meio
 * da lógica. A ordem de saída é da mais grave para a menos grave, e dentro da mesma gravidade a
 * ordem em que as regras estão declaradas.
 */
@Component
public class TriageRiskEvaluator {

  /** Dívida de cartão/cheque especial a partir desta fração da renda anual é alta. */
  static final BigDecimal DEBT_HIGH_RATIO = new BigDecimal("0.30");

  /** Dívida de cartão/cheque especial a partir desta fração da renda anual é média. */
  static final BigDecimal DEBT_MEDIUM_RATIO = new BigDecimal("0.10");

  /** Sem renda informada não há como medir o peso da dívida: assume-se gravidade média. */
  static final RiskSeverity DEBT_WITHOUT_INCOME_SEVERITY = RiskSeverity.MEDIUM;

  static final int MONTHS_IN_YEAR = 12;

  /** Interesses de investimento tratados como de risco na regra "investe em risco sem reserva". */
  static final Set<String> RISKY_INVESTMENT_INTERESTS = Set.of("STOCKS", "CRYPTO");

  static final Set<DebtType> COSTLY_DEBT_TYPES = Set.of(DebtType.CREDIT_CARD, DebtType.OVERDRAFT);

  /**
   * Avalia as respostas. {@code monthlyIncome} vem do perfil (PATCH de informações complementares),
   * não do questionário, e pode ser nulo.
   */
  public List<TriageRisk> evaluate(TriageAnswers a, BigDecimal monthlyIncome) {
    List<TriageRisk> risks = new ArrayList<>();
    overspending(a, risks);
    costlyDebt(a, monthlyIncome, risks);
    latePayments(a, risks);
    noEmergencyReserve(a, risks);
    dependentsWithoutReserve(a, risks);
    unstableIncome(a, risks);
    impulsiveSpending(a, risks);
    riskyHabits(a, risks);
    riskyInvestingWithoutReserve(a, risks);
    risks.sort(Comparator.comparing(TriageRisk::severity).reversed());
    return List.copyOf(risks);
  }

  /** Perfil de investidor que a reação a uma queda de 20% define. */
  public FinancialProfile profileOf(TriageAnswers a) {
    return a.marketDropReaction().profile();
  }

  private void overspending(TriageAnswers a, List<TriageRisk> out) {
    if (a.incomeExpenseRelation() == IncomeExpenseRelation.SPENDS_MORE) {
      out.add(
          risk(
              "OVERSPENDING",
              RiskSeverity.HIGH,
              "Você gasta mais do que ganha",
              "Seus gastos estão acima da sua renda, o que costuma levar a dívidas.",
              "Liste seus gastos e corte primeiro os que não são essenciais."));
    }
  }

  private void costlyDebt(TriageAnswers a, BigDecimal monthlyIncome, List<TriageRisk> out) {
    if (!a.hasDebt() || a.debtTypes().stream().noneMatch(COSTLY_DEBT_TYPES::contains)) {
      return;
    }
    out.add(
        risk(
            "COSTLY_DEBT",
            debtSeverity(a.debtTotalAmount(), monthlyIncome),
            "Dívida de cartão ou cheque especial",
            "Essas dívidas têm os juros mais altos do mercado e crescem rápido.",
            "Priorize quitá-las e, se possível, troque por uma dívida com juros menores."));
  }

  private static RiskSeverity debtSeverity(BigDecimal debtTotal, BigDecimal monthlyIncome) {
    if (monthlyIncome == null || monthlyIncome.signum() <= 0 || debtTotal == null) {
      return DEBT_WITHOUT_INCOME_SEVERITY;
    }
    BigDecimal annualIncome = monthlyIncome.multiply(BigDecimal.valueOf(MONTHS_IN_YEAR));
    if (debtTotal.compareTo(annualIncome.multiply(DEBT_HIGH_RATIO)) >= 0) {
      return RiskSeverity.HIGH;
    }
    return debtTotal.compareTo(annualIncome.multiply(DEBT_MEDIUM_RATIO)) >= 0
        ? RiskSeverity.MEDIUM
        : RiskSeverity.LOW;
  }

  private void latePayments(TriageAnswers a, List<TriageRisk> out) {
    if (a.latePayments()) {
      out.add(
          risk(
              "LATE_PAYMENTS",
              RiskSeverity.MEDIUM,
              "Contas atrasadas",
              "Você atrasou contas nos últimos 6 meses, o que gera multa, juros e afeta seu crédito.",
              "Organize as datas de vencimento e considere o débito automático."));
    }
  }

  private void noEmergencyReserve(TriageAnswers a, List<TriageRisk> out) {
    if (a.emergencyReserve() == EmergencyReserve.NONE) {
      out.add(
          risk(
              "NO_EMERGENCY_RESERVE",
              RiskSeverity.MEDIUM,
              "Sem reserva de emergência",
              "Sem reserva, um imprevisto vira dívida.",
              "Comece guardando um valor fixo todo mês até cobrir 3 meses de gastos."));
    }
  }

  private void dependentsWithoutReserve(TriageAnswers a, List<TriageRisk> out) {
    boolean hasDependents = a.childrenCount() + a.dependentsCount() > 0;
    if (hasDependents && a.emergencyReserve() == EmergencyReserve.NONE) {
      out.add(
          risk(
              "DEPENDENTS_WITHOUT_RESERVE",
              RiskSeverity.HIGH,
              "Filhos ou dependentes sem reserva",
              "Outras pessoas dependem de você e não há reserva para cobrir um imprevisto.",
              "Torne a reserva de emergência a sua prioridade antes de qualquer investimento."));
    }
  }

  private void unstableIncome(TriageAnswers a, List<TriageRisk> out) {
    if (a.incomeType() == IncomeType.VARIABLE) {
      out.add(unstableIncomeRisk(RiskSeverity.MEDIUM));
    } else if (a.incomeType() == IncomeType.MIXED) {
      out.add(unstableIncomeRisk(RiskSeverity.LOW));
    }
  }

  private static TriageRisk unstableIncomeRisk(RiskSeverity severity) {
    return risk(
        "UNSTABLE_INCOME",
        severity,
        "Renda instável",
        "Uma renda que varia de mês a mês dificulta o planejamento.",
        "Planeje pelo seu mês mais fraco e guarde nos meses melhores.");
  }

  private void impulsiveSpending(TriageAnswers a, List<TriageRisk> out) {
    if (a.spendingHabit() == SpendingHabit.IMPULSIVE) {
      out.add(
          risk(
              "IMPULSIVE_SPENDING",
              RiskSeverity.MEDIUM,
              "Gastos por impulso",
              "Compras não planejadas pesam no orçamento sem você perceber.",
              "Espere 24 horas antes de compras que não estavam planejadas."));
    }
  }

  private void riskyHabits(TriageAnswers a, List<TriageRisk> out) {
    if (a.riskyHabits().isEmpty()) {
      return;
    }
    RiskSeverity severity =
        a.riskyHabits().contains(RiskyHabit.BETS) ? RiskSeverity.HIGH : RiskSeverity.MEDIUM;
    out.add(
        risk(
            "RISKY_HABITS",
            severity,
            "Gastos com hábitos de risco",
            "Apostas, bebida e cigarro consomem dinheiro e saúde, e em geral crescem com o tempo.",
            "Defina um teto mensal para esses gastos e acompanhe-o."));
  }

  private void riskyInvestingWithoutReserve(TriageAnswers a, List<TriageRisk> out) {
    boolean riskyAppetite =
        a.marketDropReaction() == MarketDropReaction.BUY_MORE
            || a.investmentInterests().stream().anyMatch(RISKY_INVESTMENT_INTERESTS::contains);
    if (a.invests() && riskyAppetite && a.emergencyReserve() == EmergencyReserve.NONE) {
      out.add(
          risk(
              "RISKY_INVESTING_WITHOUT_RESERVE",
              RiskSeverity.HIGH,
              "Investe em risco sem reserva",
              "Investimentos de risco podem cair justo quando você precisar do dinheiro.",
              "Monte a reserva de emergência em um investimento seguro antes de arriscar mais."));
    }
  }

  private static TriageRisk risk(
      String code, RiskSeverity severity, String title, String description, String suggestion) {
    return new TriageRisk(code, severity, title, description, suggestion);
  }
}
