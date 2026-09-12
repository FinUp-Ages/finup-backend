package br.com.finup.repository;

import br.com.finup.model.FinUpScoreInputs;
import br.com.finup.model.IncomeExpenseRelation;
import br.com.finup.model.SpendingHabit;
import br.com.finup.model.User;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * Implementacao temporaria em memoria, no mesmo espirito do {@link InMemoryUserRepository}: nao
 * existe ainda entidade/consulta JPA para {@code UserFinancialProfiles}, {@code Transactions},
 * {@code Debts}, {@code Goals} e {@code Investments} (isso e escopo da tarefa de modelagem de
 * dados, ainda nao integrada ao codigo).
 *
 * <p>Para os 3 e-mails de demonstracao abaixo (ver {@code FinUpScoreDemoSeeder}), devolve os
 * fixtures que reproduzem, numero a numero, os Exemplos A/C/D de {@code docs/finup-score.md} — o
 * {@code InMemoryFinUpScoreDataProviderTest} trava essa correspondencia para o codigo e o documento
 * nunca divergirem. Para qualquer outro usuario (ex.: recem-cadastrado via {@code POST
 * /api/v1/users}), devolve "sem dado nenhum", o que faz o calculo cair no caminho de dado
 * insuficiente quando a renda tambem nao foi informada.
 *
 * <p><strong>Substituir quando o PostgreSQL entrar:</strong> apague esta classe e implemente {@link
 * FinUpScoreDataProvider} com as consultas reais as 5 tabelas. Nem o service nem o calculador mudam
 * — e esse o motivo de a interface existir.
 */
@Repository
public class InMemoryFinUpScoreDataProvider implements FinUpScoreDataProvider {

  /** E-mail do usuario de demonstracao que reproduz o Exemplo A (score 696) do documento. */
  public static final String DEMO_FALLBACK_ESTIMATE_EMAIL = "exemplo.a.fallback@finup.local";

  /** E-mail do usuario de demonstracao que reproduz o Exemplo C (score 950) do documento. */
  public static final String DEMO_HEALTHY_EMAIL = "exemplo.c.saudavel@finup.local";

  /** E-mail do usuario de demonstracao que reproduz o Exemplo D (score 106) do documento. */
  public static final String DEMO_STRUGGLING_EMAIL = "exemplo.d.dificuldade@finup.local";

  private final Map<String, FinUpScoreInputs> fixturesByEmail =
      Map.of(
          DEMO_FALLBACK_ESTIMATE_EMAIL,
          new FinUpScoreInputs(
              new BigDecimal("5000"),
              null,
              new BigDecimal("3000"),
              IncomeExpenseRelation.BALANCED,
              new BigDecimal("2000"),
              false,
              new BigDecimal("2150"),
              0.30,
              SpendingHabit.MODERATE),
          DEMO_HEALTHY_EMAIL,
          new FinUpScoreInputs(
              new BigDecimal("8000"),
              null,
              new BigDecimal("5000"),
              null,
              BigDecimal.ZERO,
              false,
              new BigDecimal("50000"),
              null,
              SpendingHabit.CONTROLLED),
          DEMO_STRUGGLING_EMAIL,
          new FinUpScoreInputs(
              new BigDecimal("3000"),
              null,
              new BigDecimal("3900"),
              null,
              new BigDecimal("20000"),
              true,
              BigDecimal.ZERO,
              null,
              SpendingHabit.IMPULSIVE));

  @Override
  public FinUpScoreInputs loadInputs(User user) {
    FinUpScoreInputs fixture = fixturesByEmail.get(user.getEmail().toLowerCase());
    if (fixture != null) {
      return fixture;
    }
    return new FinUpScoreInputs(
        user.getMonthlyIncome(),
        null,
        null,
        null,
        BigDecimal.ZERO,
        false,
        BigDecimal.ZERO,
        null,
        null);
  }
}
