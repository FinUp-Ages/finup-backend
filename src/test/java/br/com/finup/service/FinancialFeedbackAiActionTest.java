package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.finup.dto.TransactionListItemResponse;
import br.com.finup.dto.TransactionListResponse;
import br.com.finup.model.AiModel;
import br.com.finup.model.Category;
import br.com.finup.model.TransactionType;
import br.com.finup.model.User;
import br.com.finup.security.AuthenticatedIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Garante que os numeros do feedback sao calculados no codigo e que so agregados vao ao modelo. */
@ExtendWith(MockitoExtension.class)
class FinancialFeedbackAiActionTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
  private static final AuthenticatedIdentity IDENTITY =
      new AuthenticatedIdentity("sub", "Ana", "ana@exemplo.com");
  private static final LocalDate MONTH_START = LocalDate.of(2026, 10, 1);
  private static final LocalDate PREVIOUS_START = LocalDate.of(2026, 9, 1);
  private static final LocalDate PREVIOUS_END = LocalDate.of(2026, 9, 30);

  @Mock private TransactionService transactionService;
  @Mock private BedrockLlmService llmService;

  private final User user = mock(User.class);
  private final UUID educationId = UUID.randomUUID();
  private final UUID foodId = UUID.randomUUID();
  private final Category education = category(educationId, "Educação");
  private final UUID betsId = UUID.randomUUID();
  private final Category food = category(foodId, "Alimentação");
  private final Category bets = category(betsId, "Bets");

  private FinancialFeedbackAiAction action;

  private static Category category(UUID id, String name) {
    Category category = mock(Category.class);
    lenient().when(category.getId()).thenReturn(id);
    lenient().when(category.getName()).thenReturn(name);
    return category;
  }

  private static TransactionListItemResponse item(
      UUID categoryId, TransactionType type, String amount, String description) {
    return new TransactionListItemResponse(
        UUID.randomUUID(),
        categoryId,
        null,
        type,
        description,
        new BigDecimal(amount),
        TODAY,
        false,
        Instant.now());
  }

  private AiAction.Context context(String message) {
    return new AiAction.Context(
        IDENTITY, user, List.of(education, food, bets), message, TODAY, AiModel.ANTHROPIC);
  }

  @BeforeEach
  void setUp() {
    action = new FinancialFeedbackAiAction(transactionService, llmService);
  }

  @Test
  @DisplayName("calcula totais e maiores gastos no codigo e entrega so agregados ao modelo")
  void sendsComputedAggregatesToModel() {
    when(user.getMonthlyIncome()).thenReturn(new BigDecimal("3500.00"));
    when(user.getFinUpScore()).thenReturn(72);
    when(transactionService.list(IDENTITY, MONTH_START, TODAY))
        .thenReturn(
            new TransactionListResponse(
                new BigDecimal("1980.00"),
                List.of(
                    item(foodId, TransactionType.EXPENSE, "300.00", "jantar com a Maria"),
                    item(educationId, TransactionType.EXPENSE, "720.00", "mensalidade pucrs"),
                    item(foodId, TransactionType.EXPENSE, "500.00", "mercado"),
                    item(null, TransactionType.INCOME, "3500.00", "salario"))));
    when(transactionService.list(IDENTITY, PREVIOUS_START, PREVIOUS_END))
        .thenReturn(
            new TransactionListResponse(
                new BigDecimal("1000.00"),
                List.of(
                    item(foodId, TransactionType.EXPENSE, "2000.00", "x"),
                    item(null, TransactionType.INCOME, "3000.00", "y"))));
    when(llmService.invoke(any(), any(), any())).thenReturn("Voce esta bem, mas cuidado.");

    AiAction.Result result =
        action.execute(
            context("posso gastar 200 num jantar?"), new ObjectMapper().createObjectNode());

    assertThat(result.message()).isEqualTo("Voce esta bem, mas cuidado.");
    assertThat(result.transaction()).isNull();

    ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
    verify(llmService)
        .invoke(system.capture(), eq("posso gastar 200 num jantar?"), eq(AiModel.ANTHROPIC));
    assertThat(system.getValue())
        .contains("Renda mensal declarada: R$ 3500.00")
        .contains("FinUp Score: 72")
        .contains("receitas R$ 3500.00, despesas R$ 1520.00, saldo R$ 1980.00")
        .contains("receitas R$ 3000.00, despesas R$ 2000.00, saldo R$ 1000.00")
        // Alimentacao (800) vem antes de Educacao (720); 800/1520 = 52.6%, 720/1520 = 47.4%
        .containsSubsequence("Alimentação: R$ 800.00 (52.6%)", "Educação: R$ 720.00 (47.4%)")
        .doesNotContain("jantar com a Maria", "mensalidade pucrs", "ana@exemplo.com");
  }

  @Test
  @DisplayName("aponta possiveis duplicados e maiores despesas, sem descricao nem id")
  void flagsPossibleDuplicatesAndLargestExpenses() {
    when(transactionService.list(IDENTITY, MONTH_START, TODAY))
        .thenReturn(
            new TransactionListResponse(
                new BigDecimal("-1100.00"),
                List.of(
                    item(foodId, TransactionType.EXPENSE, "50.00", "pizza"),
                    item(foodId, TransactionType.EXPENSE, "50.00", "pizza de novo"),
                    item(educationId, TransactionType.EXPENSE, "900.00", "matricula"),
                    item(foodId, TransactionType.EXPENSE, "100.00", "mercado"))));
    when(transactionService.list(IDENTITY, PREVIOUS_START, PREVIOUS_END))
        .thenReturn(new TransactionListResponse(BigDecimal.ZERO, List.of()));
    when(llmService.invoke(any(), any(), any())).thenReturn("ok");

    action.execute(
        context("fiz algum gasto errado esse mes?"), new ObjectMapper().createObjectNode());

    ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
    verify(llmService).invoke(system.capture(), any(), any());
    assertThat(system.getValue())
        .contains("- 2 despesas de R$ 50.00 em Alimentação no dia 2026-10-15")
        .containsSubsequence(
            "Maiores despesas individuais",
            "R$ 900.00 em Educação",
            "R$ 100.00 em Alimentação",
            "R$ 50.00 em Alimentação")
        .doesNotContain("pizza", "matricula", educationId.toString());
  }

  @Test
  @DisplayName("gastos de risco entram sempre no prompt, com mes atual e anterior")
  void includesRiskySpendingWithPreviousMonth() {
    when(transactionService.list(IDENTITY, MONTH_START, TODAY))
        .thenReturn(
            new TransactionListResponse(
                new BigDecimal("-400.00"),
                List.of(
                    item(betsId, TransactionType.EXPENSE, "100.00", "aposta no jogo"),
                    item(betsId, TransactionType.EXPENSE, "50.00", "outra"),
                    item(foodId, TransactionType.EXPENSE, "250.00", "mercado"))));
    when(transactionService.list(IDENTITY, PREVIOUS_START, PREVIOUS_END))
        .thenReturn(
            new TransactionListResponse(
                new BigDecimal("-80.00"),
                List.of(item(betsId, TransactionType.EXPENSE, "80.00", "x"))));
    when(llmService.invoke(any(), any(), any())).thenReturn("ok");

    action.execute(context("qual meu saldo?"), new ObjectMapper().createObjectNode());

    ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
    verify(llmService).invoke(system.capture(), any(), any());
    assertThat(system.getValue())
        .contains("Gastos de risco (considerados evitaveis):")
        .contains(
            "- Apostas/bets: R$ 150.00 no mes atual (37.5% das despesas), R$ 80.00 no mes anterior")
        .contains("DEVE mencionar")
        .doesNotContain("aposta no jogo");
  }

  @Test
  @DisplayName("sem gastos de risco a secao diz que nenhum foi registrado")
  void riskySectionSaysNoneWhenAbsent() {
    when(transactionService.list(IDENTITY, MONTH_START, TODAY))
        .thenReturn(
            new TransactionListResponse(
                new BigDecimal("-50.00"),
                List.of(item(foodId, TransactionType.EXPENSE, "50.00", "lanche"))));
    when(transactionService.list(IDENTITY, PREVIOUS_START, PREVIOUS_END))
        .thenReturn(new TransactionListResponse(BigDecimal.ZERO, List.of()));
    when(llmService.invoke(any(), any(), any())).thenReturn("ok");

    action.execute(context("estou bem?"), new ObjectMapper().createObjectNode());

    ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
    verify(llmService).invoke(system.capture(), any(), any());
    assertThat(system.getValue())
        .containsSubsequence("Gastos de risco (considerados evitaveis):", "- nenhum registrado");
  }

  @Test
  @DisplayName("sem duplicados informa que nenhum foi encontrado")
  void reportsNoDuplicates() {
    when(transactionService.list(IDENTITY, MONTH_START, TODAY))
        .thenReturn(
            new TransactionListResponse(
                new BigDecimal("-50.00"),
                List.of(item(foodId, TransactionType.EXPENSE, "50.00", "lanche"))));
    when(transactionService.list(IDENTITY, PREVIOUS_START, PREVIOUS_END))
        .thenReturn(new TransactionListResponse(BigDecimal.ZERO, List.of()));
    when(llmService.invoke(any(), any(), any())).thenReturn("ok");

    action.execute(context("fiz algo errado?"), new ObjectMapper().createObjectNode());

    ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
    verify(llmService).invoke(system.capture(), any(), any());
    assertThat(system.getValue())
        .containsSubsequence("Possiveis lancamentos duplicados no mes atual:", "- nenhum");
  }

  @Test
  @DisplayName("sem renda declarada e sem score avisa o modelo em vez de inventar")
  void flagsMissingIncomeAndScore() {
    when(user.getMonthlyIncome()).thenReturn(null);
    when(user.getFinUpScore()).thenReturn(null);
    when(transactionService.list(IDENTITY, MONTH_START, TODAY))
        .thenReturn(
            new TransactionListResponse(
                new BigDecimal("-50.00"),
                List.of(item(foodId, TransactionType.EXPENSE, "50.00", "lanche"))));
    when(transactionService.list(IDENTITY, PREVIOUS_START, PREVIOUS_END))
        .thenReturn(new TransactionListResponse(BigDecimal.ZERO, List.of()));
    when(llmService.invoke(any(), any(), any())).thenReturn("ok");

    action.execute(context("estou bem?"), new ObjectMapper().createObjectNode());

    ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
    verify(llmService).invoke(system.capture(), any(), any());
    assertThat(system.getValue())
        .contains("Renda mensal declarada: nao informada")
        .contains("FinUp Score: nao calculado");
  }

  @Test
  @DisplayName("a instrucao de classificacao cobre perguntas de saldo e de resumo")
  void classificationCoversBalanceQuestions() {
    assertThat(action.promptInstructions())
        .contains("qual meu saldo?", "resumo do mes", "gasto errado", "FINANCIAL_FEEDBACK");
  }

  @Test
  @DisplayName("sem nenhuma movimentacao responde direto, sem chamar o modelo")
  void noTransactionsSkipsModel() {
    when(transactionService.list(any(), any(), any()))
        .thenReturn(new TransactionListResponse(BigDecimal.ZERO, List.of()));

    AiAction.Result result =
        action.execute(context("estou bem?"), new ObjectMapper().createObjectNode());

    assertThat(result.message()).contains("Registre alguns gastos");
    verifyNoInteractions(llmService);
  }
}
