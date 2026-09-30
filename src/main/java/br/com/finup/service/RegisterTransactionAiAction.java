package br.com.finup.service;

import br.com.finup.exception.BusinessException;
import br.com.finup.model.Category;
import br.com.finup.model.Transaction;
import br.com.finup.model.TransactionType;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * "Gastei 7 reais na PUCRS" vira uma transacao EXPENSE de 7,00 na categoria Educacao, registrada
 * pelo mesmo {@link TransactionService} usado pelo endpoint tradicional.
 *
 * <p>O modelo so sugere; quem decide e este codigo: a categoria precisa ser do usuario (propria ou
 * padrao) e compativel com o tipo, o valor precisa ser positivo e caber na coluna, e a data nao
 * pode ser futura.
 */
@Component
public class RegisterTransactionAiAction implements AiAction {

  public static final String NAME = "REGISTER_TRANSACTION";

  private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
  private static final int MAX_DESCRIPTION = 255;

  private final TransactionService transactionService;

  public RegisterTransactionAiAction(TransactionService transactionService) {
    this.transactionService = transactionService;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String promptInstructions() {
    return """
        %s: o usuario relata um gasto (gastei, paguei, comprei) ou um ganho (recebi, ganhei).
        Formato: {"action":"%s","type":"EXPENSE|INCOME","amount":<numero positivo>,\
        "category":"<nome exato de uma categoria da lista com o mesmo tipo, ou null se nenhuma servir>",\
        "description":"<resumo de ate 100 caracteres>","date":"<YYYY-MM-DD, ou null se nao informada>"}"""
        .formatted(NAME, NAME);
  }

  @Override
  public Result execute(Context context, JsonNode payload) {
    TransactionType type = parseType(payload.path("type"));
    BigDecimal amount = parseAmount(payload.path("amount"));
    Category category = findCategory(context, type, payload.path("category"));
    LocalDate date = parseDate(payload.path("date"), context.today());
    String description = parseDescription(payload.path("description"), context.originalMessage());

    Transaction transaction =
        transactionService.register(
            context.identity(),
            category.getId(),
            null,
            type,
            description,
            amount,
            date,
            false,
            null);

    String message =
        "%s de R$ %s registrada em %s."
            .formatted(
                type == TransactionType.EXPENSE ? "Despesa" : "Receita",
                amount.toPlainString(),
                category.getName());
    return new Result(message, transaction);
  }

  private TransactionType parseType(JsonNode node) {
    try {
      return TransactionType.valueOf(node.asText("").trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw unprocessable("Nao consegui identificar se e uma despesa ou uma receita.");
    }
  }

  private BigDecimal parseAmount(JsonNode node) {
    BigDecimal amount;
    try {
      amount =
          node.isNumber()
              ? node.decimalValue()
              : new BigDecimal(node.asText("").trim().replace(",", "."));
    } catch (NumberFormatException e) {
      throw unprocessable("Nao consegui identificar o valor.");
    }
    amount = amount.setScale(2, RoundingMode.HALF_UP);
    if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
      throw unprocessable("O valor informado e invalido.");
    }
    return amount;
  }

  private Category findCategory(Context context, TransactionType type, JsonNode node) {
    String wanted = normalize(node.isNull() ? "" : node.asText(""));
    return context.categories().stream()
        .filter(category -> category.getType() == type)
        .filter(category -> normalize(category.getName()).equals(wanted))
        .findFirst()
        .orElseThrow(
            () ->
                unprocessable("Nao encontrei uma categoria compativel. Escolha uma manualmente."));
  }

  private LocalDate parseDate(JsonNode node, LocalDate today) {
    if (node.isNull() || node.isMissingNode() || node.asText("").isBlank()) {
      return today;
    }
    try {
      LocalDate date = LocalDate.parse(node.asText().trim());
      if (date.isAfter(today)) {
        throw unprocessable("A data da transacao nao pode ser futura.");
      }
      return date;
    } catch (DateTimeParseException e) {
      return today;
    }
  }

  private String parseDescription(JsonNode node, String originalMessage) {
    String text = node.isNull() ? "" : node.asText("").strip();
    if (text.isEmpty()) {
      text = originalMessage.strip();
    }
    return text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION) : text;
  }

  /** Ignora acento e caixa: "educacao" casa com "Educação". */
  private static String normalize(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .strip()
        .toLowerCase(Locale.ROOT);
  }

  private static BusinessException unprocessable(String message) {
    return new BusinessException(message);
  }
}
