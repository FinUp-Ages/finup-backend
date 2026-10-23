package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.finup.dto.SpendingAlertItemResponse;
import br.com.finup.dto.SpendingAlertsResponse;
import br.com.finup.exception.InvalidSpendingAlertPeriodException;
import br.com.finup.model.AlertSource;
import br.com.finup.model.Category;
import br.com.finup.model.RiskKind;
import br.com.finup.model.Transaction;
import br.com.finup.model.TransactionType;
import br.com.finup.model.User;
import br.com.finup.repository.CategoryRepository;
import br.com.finup.repository.TransactionRepository;
import br.com.finup.security.AuthenticatedIdentity;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SpendingAlertServiceTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-10-21T12:00:00Z");
  private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
  private static final Clock CLOCK = Clock.fixed(FIXED_INSTANT, ZONE);
  private static final LocalDate TODAY = LocalDate.now(CLOCK);

  private static final AuthenticatedIdentity IDENTITY =
      new AuthenticatedIdentity("sub-123", "Gabriel", "gabriel@example.com");

  @Mock private TransactionRepository transactionRepository;
  @Mock private CategoryRepository categoryRepository;
  @Mock private UserService userService;
  @Mock private AiSpendingClassifier aiClassifier;

  private final User user =
      User.createFromCognitoIdentity("sub-123", "Gabriel", "gabriel@example.com");
  private final UUID userId = UUID.randomUUID();

  private SpendingAlertService service;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(user, "id", userId);
    lenient().when(userService.findByAuthenticatedIdentity(IDENTITY)).thenReturn(user);

    service =
        new SpendingAlertService(
            transactionRepository, categoryRepository, userService, aiClassifier, CLOCK);
  }

  @Test
  @DisplayName("gera alerta por regra para BETS no formato exato do contrato C1")
  void generatesRuleAlertForBetsMatchingContractC1() {
    UUID betsCatId = UUID.randomUUID();
    Category betsCat = createCategory(betsCatId, "Apostas", TransactionType.EXPENSE);

    List<Transaction> transactions =
        List.of(
            createExpense(betsCatId, "750.00", TODAY),
            createExpense(betsCatId, "750.00", TODAY.minusDays(5)),
            createExpense(betsCatId, "1000.00", TODAY.minusDays(10)),
            createExpense(betsCatId, "500.00", TODAY.minusDays(20)));

    when(transactionRepository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
            userId, TODAY.minusDays(30), TODAY))
        .thenReturn(transactions);
    when(categoryRepository.findByUserOrIsDefaultTrue(user)).thenReturn(List.of(betsCat));

    SpendingAlertsResponse response = service.getSpendingAlerts(IDENTITY, 30);

    assertThat(response.periodDays()).isEqualTo(30);
    assertThat(response.alerts()).hasSize(1);

    SpendingAlertItemResponse alert = response.alerts().get(0);
    assertThat(alert.categoryId()).isEqualTo(betsCatId);
    assertThat(alert.categoryName()).isEqualTo("Apostas");
    assertThat(alert.riskKind()).isEqualTo(RiskKind.BETS);
    assertThat(alert.totalAmount()).isEqualTo(new BigDecimal("3000.00"));
    assertThat(alert.transactionCount()).isEqualTo(4);
    assertThat(alert.message()).isEqualTo("R$ 3.000,00 em apostas nos últimos 30 dias.");
    assertThat(alert.source()).isEqualTo(AlertSource.RULE);
    assertThat(alert.projection().years()).isEqualTo(5);
    assertThat(alert.projection().annualRatePct()).isEqualTo(10);
    assertThat(alert.projection().futureValue()).isEqualTo(new BigDecimal("4831.53"));

    // Como categoria Apostas combina com a regra, IA nao deve ser consultada para ela
    verifyNoInteractions(aiClassifier);
  }

  @Test
  @DisplayName("reconhece gastos com cigarro e tabaco como regra (TOBACCO)")
  void recognizesTobaccoAsRuleAlert() {
    UUID tobaccoCatId = UUID.randomUUID();
    Category tobaccoCat = createCategory(tobaccoCatId, "Tabacaria", TransactionType.EXPENSE);

    List<Transaction> transactions =
        List.of(
            createExpense(tobaccoCatId, "60.00", TODAY),
            createExpense(tobaccoCatId, "40.00", TODAY.minusDays(2)));

    when(transactionRepository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
            userId, TODAY.minusDays(30), TODAY))
        .thenReturn(transactions);
    when(categoryRepository.findByUserOrIsDefaultTrue(user)).thenReturn(List.of(tobaccoCat));

    SpendingAlertsResponse response = service.getSpendingAlerts(IDENTITY, 30);

    assertThat(response.alerts()).hasSize(1);
    SpendingAlertItemResponse alert = response.alerts().get(0);
    assertThat(alert.riskKind()).isEqualTo(RiskKind.TOBACCO);
    assertThat(alert.source()).isEqualTo(AlertSource.RULE);
    assertThat(alert.totalAmount()).isEqualTo(new BigDecimal("100.00"));
    assertThat(alert.transactionCount()).isEqualTo(2);
    assertThat(alert.message()).isEqualTo("R$ 100,00 em tabacaria nos últimos 30 dias.");
    assertThat(alert.projection().futureValue()).isEqualTo(new BigDecimal("161.05"));
  }

  @Test
  @DisplayName("classifica despesas fora da regra usando IA, retornando source AI e riskKind OTHER")
  void classifiesWithAiWhenOutsideRuleList() {
    UUID leisureCatId = UUID.randomUUID();
    Category leisureCat = createCategory(leisureCatId, "Lazer", TransactionType.EXPENSE);

    Transaction cinema = createExpense(leisureCatId, "50.00", TODAY, "Cinema com pipoca");
    Transaction rifa =
        createExpense(leisureCatId, "200.00", TODAY.minusDays(3), "Rifa de moto esportiva");
    Transaction bicho =
        createExpense(leisureCatId, "100.00", TODAY.minusDays(7), "Jogo do bicho milhar");

    when(transactionRepository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
            userId, TODAY.minusDays(30), TODAY))
        .thenReturn(List.of(cinema, rifa, bicho));
    when(categoryRepository.findByUserOrIsDefaultTrue(user)).thenReturn(List.of(leisureCat));

    when(aiClassifier.isRisky("Lazer", "Cinema com pipoca")).thenReturn(false);
    when(aiClassifier.isRisky("Lazer", "Rifa de moto esportiva")).thenReturn(true);
    when(aiClassifier.isRisky("Lazer", "Jogo do bicho milhar")).thenReturn(true);

    SpendingAlertsResponse response = service.getSpendingAlerts(IDENTITY, 30);

    assertThat(response.alerts()).hasSize(1);
    SpendingAlertItemResponse alert = response.alerts().get(0);
    assertThat(alert.categoryId()).isEqualTo(leisureCatId);
    assertThat(alert.categoryName()).isEqualTo("Lazer");
    assertThat(alert.riskKind()).isEqualTo(RiskKind.OTHER);
    assertThat(alert.source()).isEqualTo(AlertSource.AI);
    // Somente os gastos julgados ruins entram no total e count
    assertThat(alert.totalAmount()).isEqualTo(new BigDecimal("300.00"));
    assertThat(alert.transactionCount()).isEqualTo(2);
    assertThat(alert.message()).isEqualTo("R$ 300,00 em lazer nos últimos 30 dias.");
    assertThat(alert.projection().futureValue()).isEqualTo(new BigDecimal("483.15"));
  }

  @Test
  @DisplayName("falha da IA nao derruba o servico e devolve apenas os alertas por regra")
  void aiFailureDoesNotCrashAndReturnsOnlyRuleAlerts() {
    UUID alcoholCatId = UUID.randomUUID();
    Category alcoholCat = createCategory(alcoholCatId, "Cerveja", TransactionType.EXPENSE);

    UUID unknownCatId = UUID.randomUUID();
    Category unknownCat = createCategory(unknownCatId, "Outros", TransactionType.EXPENSE);

    Transaction beer = createExpense(alcoholCatId, "80.00", TODAY);
    Transaction unknownTx = createExpense(unknownCatId, "120.00", TODAY, "Compra duvidosa");

    when(transactionRepository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
            userId, TODAY.minusDays(30), TODAY))
        .thenReturn(List.of(beer, unknownTx));
    when(categoryRepository.findByUserOrIsDefaultTrue(user))
        .thenReturn(List.of(alcoholCat, unknownCat));

    // Se o classificador da IA retornar false (como acontece no fallback de erro)
    when(aiClassifier.isRisky("Outros", "Compra duvidosa")).thenReturn(false);

    SpendingAlertsResponse response = service.getSpendingAlerts(IDENTITY, 30);

    assertThat(response.alerts()).hasSize(1);
    assertThat(response.alerts().get(0).categoryName()).isEqualTo("Cerveja");
    assertThat(response.alerts().get(0).riskKind()).isEqualTo(RiskKind.ALCOHOL);
    assertThat(response.alerts().get(0).source()).isEqualTo(AlertSource.RULE);
  }

  @Test
  @DisplayName("ignora transacoes do tipo INCOME")
  void ignoresIncomeTransactions() {
    UUID betsCatId = UUID.randomUUID();
    Category betsCat = createCategory(betsCatId, "Apostas", TransactionType.EXPENSE);

    Transaction income =
        Transaction.register(
            userId,
            betsCatId,
            null,
            TransactionType.INCOME,
            "Premio aposta",
            new BigDecimal("500.00"),
            TODAY,
            null);

    when(transactionRepository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
            userId, TODAY.minusDays(30), TODAY))
        .thenReturn(List.of(income));

    SpendingAlertsResponse response = service.getSpendingAlerts(IDENTITY, 30);

    assertThat(response.alerts()).isEmpty();
  }

  @Test
  @DisplayName("sem gastos de risco devolve alerts vazio")
  void returnsEmptyAlertsWhenNoRiskySpending() {
    UUID foodCatId = UUID.randomUUID();
    Category foodCat = createCategory(foodCatId, "Alimentação", TransactionType.EXPENSE);

    Transaction lunch = createExpense(foodCatId, "45.00", TODAY, "Almoço executivo");

    when(transactionRepository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
            userId, TODAY.minusDays(30), TODAY))
        .thenReturn(List.of(lunch));
    when(categoryRepository.findByUserOrIsDefaultTrue(user)).thenReturn(List.of(foodCat));
    when(aiClassifier.isRisky("Alimentação", "Almoço executivo")).thenReturn(false);

    SpendingAlertsResponse response = service.getSpendingAlerts(IDENTITY, 30);

    assertThat(response.periodDays()).isEqualTo(30);
    assertThat(response.alerts()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -30, 366})
  @DisplayName("lanca InvalidSpendingAlertPeriodException para periodo invalido")
  void throwsExceptionForInvalidDays(int invalidDays) {
    assertThatThrownBy(() -> service.getSpendingAlerts(IDENTITY, invalidDays))
        .isInstanceOf(InvalidSpendingAlertPeriodException.class);
  }

  private Category createCategory(UUID id, String name, TransactionType type) {
    Category cat = Category.createForUser(user, name, type);
    ReflectionTestUtils.setField(cat, "id", id);
    return cat;
  }

  private Transaction createExpense(UUID categoryId, String amount, LocalDate date) {
    return createExpense(categoryId, amount, date, null);
  }

  private Transaction createExpense(
      UUID categoryId, String amount, LocalDate date, String description) {
    return Transaction.register(
        userId,
        categoryId,
        null,
        TransactionType.EXPENSE,
        description,
        new BigDecimal(amount),
        date,
        null);
  }
}
