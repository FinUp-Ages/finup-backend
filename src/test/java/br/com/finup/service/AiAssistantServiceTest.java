package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.dto.AiAssistantRequest;
import br.com.finup.dto.AiAssistantResponse;
import br.com.finup.exception.AiProviderException;
import br.com.finup.exception.BusinessException;
import br.com.finup.exception.ConversationStorageException;
import br.com.finup.model.Category;
import br.com.finup.model.Transaction;
import br.com.finup.model.TransactionType;
import br.com.finup.model.User;
import br.com.finup.repository.CategoryRepository;
import br.com.finup.security.AuthenticatedIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Garante que o texto livre vira a chamada certa de transacao e que a saida do modelo e validada.
 */
@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
  private static final AuthenticatedIdentity IDENTITY =
      new AuthenticatedIdentity("sub", "Ana", "ana@exemplo.com");

  @Mock private BedrockLlmService llmService;
  @Mock private UserService userService;
  @Mock private CategoryRepository categoryRepository;
  @Mock private TransactionService transactionService;
  @Mock private ConversationService conversationService;

  private final UUID userId = UUID.randomUUID();
  private final User user = mock(User.class);
  private final UUID educationId = UUID.randomUUID();
  private final Category education = category("Educação", TransactionType.EXPENSE);
  private final Category salary = category("Salário", TransactionType.INCOME);

  private AiAssistantService service;

  private static Category category(String name, TransactionType type) {
    Category category = mock(Category.class);
    org.mockito.Mockito.lenient().when(category.getName()).thenReturn(name);
    org.mockito.Mockito.lenient().when(category.getType()).thenReturn(type);
    return category;
  }

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient().when(education.getId()).thenReturn(educationId);
    org.mockito.Mockito.lenient().when(user.getId()).thenReturn(userId);
    when(userService.findByAuthenticatedIdentity(IDENTITY)).thenReturn(user);
    org.mockito.Mockito.lenient()
        .when(categoryRepository.findByUserOrIsDefaultTrue(user))
        .thenReturn(List.of(education, salary));
    Clock clock = Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneId.of("UTC"));
    service =
        new AiAssistantService(
            llmService,
            conversationService,
            userService,
            categoryRepository,
            new ObjectMapper(),
            clock,
            List.of(new RegisterTransactionAiAction(transactionService)));
  }

  private void modelReplies(String json) {
    when(llmService.invoke(any(), any(), any())).thenReturn(json);
  }

  private AiAssistantResponse ask(String message) {
    return service.handle(IDENTITY, new AiAssistantRequest(message, null, null));
  }

  @Test
  @DisplayName("\"gastei 7 reais na pucrs\" registra despesa de 7,00 em Educacao")
  void registersExpenseFromText() {
    modelReplies(
        """
        {"action":"REGISTER_TRANSACTION","type":"EXPENSE","amount":7,"category":"Educação",
         "description":"PUCRS","date":null}""");
    when(transactionService.register(
            any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenAnswer(
            i ->
                Transaction.register(
                    userId,
                    i.getArgument(1),
                    null,
                    i.getArgument(3),
                    i.getArgument(4),
                    i.getArgument(5),
                    i.getArgument(6),
                    null));

    AiAssistantResponse response = ask("gastei 7 reais na pucrs");

    verify(transactionService)
        .register(
            eq(IDENTITY),
            eq(educationId),
            isNull(),
            eq(TransactionType.EXPENSE),
            eq("PUCRS"),
            eq(new BigDecimal("7.00")),
            eq(TODAY),
            eq(false),
            isNull());
    assertThat(response.action()).isEqualTo("REGISTER_TRANSACTION");
    assertThat(response.transaction().amount()).isEqualByComparingTo("7.00");
    assertThat(response.message()).contains("Despesa").contains("7.00").contains("Educação");
  }

  @Test
  @DisplayName("grava o turno no historico e devolve o id da conversa")
  void recordsTurnAndReturnsConversationId() {
    UUID conversationId = UUID.randomUUID();
    modelReplies(
        "{\"action\":\"REGISTER_TRANSACTION\",\"type\":\"EXPENSE\",\"amount\":7,\"category\":\"Educação\"}");
    when(transactionService.register(
            any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenAnswer(
            i ->
                Transaction.register(
                    userId,
                    i.getArgument(1),
                    null,
                    i.getArgument(3),
                    i.getArgument(4),
                    i.getArgument(5),
                    i.getArgument(6),
                    null));
    when(conversationService.recordTurn(
            eq(user), isNull(), eq("gastei 7"), eq("REGISTER_TRANSACTION"), any()))
        .thenReturn(conversationId);

    AiAssistantResponse response = ask("gastei 7");

    assertThat(response.conversationId()).isEqualTo(conversationId);
  }

  @Test
  @DisplayName("falha ao gravar o historico nao derruba a resposta ja executada")
  void historyFailureDoesNotFailTheResponse() {
    UUID conversationId = UUID.randomUUID();
    modelReplies(
        "{\"action\":\"REGISTER_TRANSACTION\",\"type\":\"EXPENSE\",\"amount\":7,\"category\":\"Educação\"}");
    when(transactionService.register(
            any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenAnswer(
            i ->
                Transaction.register(
                    userId,
                    i.getArgument(1),
                    null,
                    i.getArgument(3),
                    i.getArgument(4),
                    i.getArgument(5),
                    i.getArgument(6),
                    null));
    when(conversationService.recordTurn(any(), any(), any(), any(), any()))
        .thenThrow(new ConversationStorageException(new RuntimeException("s3 fora do ar")));

    AiAssistantResponse response =
        service.handle(IDENTITY, new AiAssistantRequest("gastei 7", null, conversationId));

    assertThat(response.action()).isEqualTo("REGISTER_TRANSACTION");
    assertThat(response.transaction()).isNotNull();
    assertThat(response.conversationId()).isEqualTo(conversationId);
  }

  @Test
  @DisplayName("conversa de outro usuario falha cedo (404), sem chamar o modelo")
  void foreignConversationFailsBeforeCallingTheModel() {
    UUID foreign = UUID.randomUUID();
    when(conversationService.requireOwned(user, foreign))
        .thenThrow(new br.com.finup.exception.ResourceNotFoundException("Conversa", foreign));

    assertThatThrownBy(() -> service.handle(IDENTITY, new AiAssistantRequest("oi", null, foreign)))
        .isInstanceOf(br.com.finup.exception.ResourceNotFoundException.class);
    org.mockito.Mockito.verifyNoInteractions(llmService);
  }

  @Test
  @DisplayName("prompt de sistema traz a data de hoje, as acoes e as categorias do usuario")
  void promptCarriesContextButNoPersonalData() {
    modelReplies("{\"action\":\"UNKNOWN\"}");

    assertThatThrownBy(() -> ask("gastei 7 reais na pucrs")).isInstanceOf(BusinessException.class);

    ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
    verify(llmService).invoke(system.capture(), message.capture(), any());
    assertThat(system.getValue())
        .contains("2026-09-30", "REGISTER_TRANSACTION", "Educação (EXPENSE)", "Salário (INCOME)")
        .doesNotContain("ana@exemplo.com");
    assertThat(message.getValue()).isEqualTo("gastei 7 reais na pucrs");
  }

  @Test
  @DisplayName("aceita JSON embrulhado em cerca de markdown, valor textual e categoria sem acento")
  void toleratesFencedJsonAndLooseFormats() {
    modelReplies(
        """
        ```json
        {"action":"REGISTER_TRANSACTION","type":"expense","amount":"7,5","category":"educacao",
         "description":"","date":"2026-09-28"}
        ```""");
    when(transactionService.register(
            any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenAnswer(
            i ->
                Transaction.register(
                    userId,
                    i.getArgument(1),
                    null,
                    i.getArgument(3),
                    i.getArgument(4),
                    i.getArgument(5),
                    i.getArgument(6),
                    null));

    ask("paguei 7,50 na pucrs");

    verify(transactionService)
        .register(
            eq(IDENTITY),
            eq(educationId),
            isNull(),
            eq(TransactionType.EXPENSE),
            eq("paguei 7,50 na pucrs"),
            eq(new BigDecimal("7.50")),
            eq(LocalDate.of(2026, 9, 28)),
            eq(false),
            isNull());
  }

  @Test
  @DisplayName("acao desconhecida vira 422 e nada e registrado")
  void unknownActionIs422() {
    modelReplies("{\"action\":\"UNKNOWN\"}");

    assertThatThrownBy(() -> ask("qual a capital da França?"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    verify(transactionService, never())
        .register(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
  }

  @Test
  @DisplayName("resposta que nao e JSON vira 502")
  void nonJsonIs502() {
    modelReplies("desculpe, nao entendi");

    assertThatThrownBy(() -> ask("gastei 7"))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
  }

  @Test
  @DisplayName("categoria inventada pelo modelo nao e aceita")
  void inventedCategoryIsRejected() {
    modelReplies(
        "{\"action\":\"REGISTER_TRANSACTION\",\"type\":\"EXPENSE\",\"amount\":7,\"category\":\"Viagens\"}");

    assertThatThrownBy(() -> ask("gastei 7 na viagem")).isInstanceOf(BusinessException.class);
    verify(transactionService, never())
        .register(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
  }

  @Test
  @DisplayName("categoria de tipo diferente do da transacao nao e aceita")
  void categoryOfOtherTypeIsRejected() {
    modelReplies(
        "{\"action\":\"REGISTER_TRANSACTION\",\"type\":\"EXPENSE\",\"amount\":7,\"category\":\"Salário\"}");

    assertThatThrownBy(() -> ask("gastei 7")).isInstanceOf(BusinessException.class);
    verify(transactionService, never())
        .register(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
  }

  @Test
  @DisplayName("valor zero, negativo ou ausente e rejeitado")
  void invalidAmountIsRejected() {
    for (String amount : List.of("0", "-5", "null", "\"abc\"")) {
      modelReplies(
          "{\"action\":\"REGISTER_TRANSACTION\",\"type\":\"EXPENSE\",\"amount\":%s,\"category\":\"Educação\"}"
              .formatted(amount));
      assertThatThrownBy(() -> ask("gastei")).isInstanceOf(BusinessException.class);
    }
    verify(transactionService, never())
        .register(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
  }

  @Test
  @DisplayName("data futura e rejeitada")
  void futureDateIsRejected() {
    modelReplies(
        "{\"action\":\"REGISTER_TRANSACTION\",\"type\":\"EXPENSE\",\"amount\":7,\"category\":\"Educação\",\"date\":\"2026-10-05\"}");

    assertThatThrownBy(() -> ask("gastei 7 amanha")).isInstanceOf(BusinessException.class);
    verify(transactionService, never())
        .register(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
  }
}
