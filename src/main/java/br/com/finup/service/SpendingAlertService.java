package br.com.finup.service;

import br.com.finup.dto.SpendingAlertItemResponse;
import br.com.finup.dto.SpendingAlertProjectionResponse;
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
import br.com.finup.service.AiSpendingClassifier.ClassificationKey;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço responsável por identificar gastos de risco e gerar alertas com projeção financeira.
 *
 * <p>A identificação combina regras determinísticas (BETS, ALCOHOL, DRUGS, TOBACCO) com
 * classificação via IA para despesas e categorias fora da lista prévia.
 */
@Service
public class SpendingAlertService {

  private static final Logger log = LoggerFactory.getLogger(SpendingAlertService.class);
  private static final int MAX_PERIOD_DAYS = 365;

  private final TransactionRepository transactionRepository;
  private final CategoryRepository categoryRepository;
  private final UserService userService;
  private final AiSpendingClassifier aiClassifier;
  private final Clock clock;

  public SpendingAlertService(
      TransactionRepository transactionRepository,
      CategoryRepository categoryRepository,
      UserService userService,
      AiSpendingClassifier aiClassifier,
      Clock clock) {
    this.transactionRepository = transactionRepository;
    this.categoryRepository = categoryRepository;
    this.userService = userService;
    this.aiClassifier = aiClassifier;
    this.clock = clock;
  }

  /**
   * Identifica gastos de risco do usuário autenticado no período de dias informado.
   *
   * @param identity identidade do usuário autenticado
   * @param days período em dias (ex: 30)
   * @return resposta estruturada com o período e a lista de alertas
   */
  @Transactional(readOnly = true)
  public SpendingAlertsResponse getSpendingAlerts(AuthenticatedIdentity identity, int days) {
    if (days <= 0 || days > MAX_PERIOD_DAYS) {
      throw new InvalidSpendingAlertPeriodException(
          "O parâmetro days deve ser maior que zero e de no máximo " + MAX_PERIOD_DAYS + " dias.");
    }

    User user = userService.findByAuthenticatedIdentity(identity);
    LocalDate today = LocalDate.now(clock);
    // Hoje conta como um dos N dias: days=30 cobre 30 datas, não 31.
    LocalDate from = today.minusDays(days - 1L);

    List<Transaction> transactions =
        transactionRepository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
            user.getId(), from, today);

    List<Transaction> expenses =
        transactions.stream().filter(t -> t.getType() == TransactionType.EXPENSE).toList();

    if (expenses.isEmpty()) {
      return new SpendingAlertsResponse(days, List.of());
    }

    List<Category> userCategories = categoryRepository.findByUserOrIsDefaultTrue(user);
    Map<UUID, Category> categoryMap =
        userCategories.stream().collect(Collectors.toMap(Category::getId, c -> c, (a, b) -> a));

    Map<UUID, List<Transaction>> expensesByCategory =
        expenses.stream().collect(Collectors.groupingBy(Transaction::getCategoryId));

    Map<UUID, String> categoryNames = new HashMap<>();
    for (UUID categoryId : expensesByCategory.keySet()) {
      Category category = categoryMap.get(categoryId);
      if (category == null) {
        category = categoryRepository.findById(categoryId).orElse(null);
      }
      categoryNames.put(categoryId, category != null ? category.getName() : "Sem categoria");
    }

    // Tudo o que está fora das regras vai junto para a IA: uma chamada por requisição, não uma por
    // transação.
    List<ClassificationKey> outsideRules =
        expensesByCategory.entrySet().stream()
            .filter(e -> RiskySpending.fromCategoryName(categoryNames.get(e.getKey())).isEmpty())
            .flatMap(
                e ->
                    e.getValue().stream()
                        .map(
                            t ->
                                new ClassificationKey(
                                    categoryNames.get(e.getKey()), t.getDescription())))
            .toList();
    Set<ClassificationKey> riskyByAi =
        outsideRules.isEmpty() ? Set.of() : aiClassifier.findRisky(outsideRules);

    List<SpendingAlertItemResponse> alerts = new ArrayList<>();

    for (Map.Entry<UUID, List<Transaction>> entry : expensesByCategory.entrySet()) {
      UUID categoryId = entry.getKey();
      List<Transaction> catExpenses = entry.getValue();
      String categoryName = categoryNames.get(categoryId);

      Optional<RiskySpending> ruleMatch = RiskySpending.fromCategoryName(categoryName);

      if (ruleMatch.isPresent()) {
        RiskKind riskKind = RiskKind.valueOf(ruleMatch.get().name());
        BigDecimal totalAmount =
            catExpenses.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int transactionCount = catExpenses.size();
        SpendingAlertProjectionResponse projection =
            SpendingAlertProjectionResponse.calculate(totalAmount);
        String message = formatMessage(totalAmount, categoryName, days);

        alerts.add(
            new SpendingAlertItemResponse(
                categoryId,
                categoryName,
                riskKind,
                totalAmount,
                transactionCount,
                message,
                projection,
                AlertSource.RULE));
      } else {
        List<Transaction> riskyExpenses =
            catExpenses.stream()
                .filter(
                    t ->
                        riskyByAi.contains(new ClassificationKey(categoryName, t.getDescription())))
                .toList();

        if (!riskyExpenses.isEmpty()) {
          BigDecimal totalAmount =
              riskyExpenses.stream()
                  .map(Transaction::getAmount)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
          int transactionCount = riskyExpenses.size();
          SpendingAlertProjectionResponse projection =
              SpendingAlertProjectionResponse.calculate(totalAmount);
          String message = formatMessage(totalAmount, categoryName, days);

          alerts.add(
              new SpendingAlertItemResponse(
                  categoryId,
                  categoryName,
                  RiskKind.OTHER,
                  totalAmount,
                  transactionCount,
                  message,
                  projection,
                  AlertSource.AI));
        }
      }
    }

    alerts.sort(
        Comparator.comparing(SpendingAlertItemResponse::totalAmount)
            .reversed()
            .thenComparing(SpendingAlertItemResponse::categoryName));

    return new SpendingAlertsResponse(days, alerts);
  }

  private String formatMessage(BigDecimal totalAmount, String categoryName, int days) {
    DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("pt-BR"));
    symbols.setGroupingSeparator('.');
    symbols.setDecimalSeparator(',');
    DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
    String formattedAmount = "R$ " + df.format(totalAmount);

    String cat =
        (categoryName != null && !categoryName.isBlank())
            ? categoryName.strip().toLowerCase(Locale.forLanguageTag("pt-BR"))
            : "gastos de risco";

    String daysText = (days == 1) ? "no último 1 dia." : "nos últimos " + days + " dias.";
    return String.format(
        Locale.forLanguageTag("pt-BR"), "%s em %s %s", formattedAmount, cat, daysText);
  }
}
