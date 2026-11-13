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
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * "Gastei 7 reais na PUCRS" vira uma transacao EXPENSE de 7,00 na categoria Educacao, registrada
 * pelo mesmo {@link TransactionService} usado pelo endpoint tradicional.
 *
 * <p>O modelo so sugere; quem decide e este codigo: o valor precisa ser positivo e caber na coluna,
 * e a data nao pode ser futura. A categoria e a do usuario (propria ou padrao) com o nome sugerido
 * e o mesmo tipo; se nao existir nenhuma com esse nome, e criada para o usuario, desde que o nome
 * seja curto e passe pelo guardrail. Nome que ja existe com o outro tipo e recusado, para nao
 * duplicar "Salario" como despesa.
 */
@Component
public class RegisterTransactionAiAction implements AiAction {

  public static final String NAME = "REGISTER_TRANSACTION";

  private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

  /** O prompt pede ate 100 caracteres; o codigo garante, sem depender do modelo. */
  static final int MAX_DESCRIPTION = 100;

  /** Nome de categoria nova: curto como no prompt, sem espaco para um comando completo. */
  static final int MAX_NEW_CATEGORY = UntrustedText.CATEGORY_NAME_MAX;

  static final int MAX_NEW_CATEGORY_WORDS = 4;

  /** Letras, numeros, espaco, hifen e "&"; sem pontuacao que separe frases. */
  private static final Pattern NEW_CATEGORY_NAME =
      Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} &-]*");

  private final TransactionService transactionService;
  private final CategoryService categoryService;

  public RegisterTransactionAiAction(
      TransactionService transactionService, CategoryService categoryService) {
    this.transactionService = transactionService;
    this.categoryService = categoryService;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String promptInstructions() {
    return """
        %s: o usuario relata um gasto (gastei, paguei, comprei) ou um ganho (recebi, ganhei) que JA ACONTECEU. Perguntas como "posso gastar?" nao sao registro.
        Formato: {"action":"%s","type":"EXPENSE|INCOME","amount":<numero positivo>,\
        "category":"<nome da categoria>",\
        "description":"<resumo de ate 100 caracteres>","date":"<YYYY-MM-DD, ou null se nao informada>"}
        Como escolher "category":
          1. Se o usuario disser a categoria (ex.: "na categoria Pets"), use esse nome, mesmo que nao \
        esteja na lista: ela sera criada.
          2. Senao, use uma categoria da lista com o mesmo tipo SO se ela descrever de fato o gasto ou \
        ganho. Pense em para quem ou para que e o gasto, nao so no tipo do item: tudo de animal de \
        estimacao (racao, veterinario, petshop) e Pets, nao Alimentação nem Saúde; corte de cabelo \
        nao e Lazer. Na duvida, nao force uma categoria da lista: va para o passo 3.
          3. Se nenhuma da lista descrever, use um nome novo, curto e generico, de 1 a 3 palavras, em \
        portugues do Brasil com a acentuacao correta e com inicial maiuscula (ex.: Pets, Cuidados \
        pessoais, Presentes, Educação, Transferência, Freelance). Ele sera criado e reaproveitado nas proximas vezes: use o nome da categoria, \
        nunca o do item (Pets, nao Racao)."""
        .formatted(NAME, NAME);
  }

  @Override
  public Result execute(Context context, JsonNode payload) {
    TransactionType type = parseType(payload.path("type"));
    BigDecimal amount = parseAmount(payload.path("amount"));
    LocalDate date = parseDate(payload.path("date"), context.today());
    String description = parseDescription(payload.path("description"), context.originalMessage());
    String categoryName = parseCategoryName(payload.path("category"));
    Category existing = findCategory(context, type, categoryName);
    Category category = existing != null ? existing : createCategory(context, type, categoryName);

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
        "%s de R$ %s registrada em %s%s."
            .formatted(
                type == TransactionType.EXPENSE ? "Despesa" : "Receita",
                amount.toPlainString(),
                category.getName(),
                existing == null ? " (categoria criada agora)" : "");
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

  /** Nome sugerido, sem as aspas com que aparece no prompt e com espacos colapsados. */
  private static String parseCategoryName(JsonNode node) {
    String text = node.isNull() ? "" : node.asText("");
    return text.replaceAll("^\\s*\"|\"\\s*$", "").replaceAll("\\s+", " ").strip();
  }

  /**
   * A categoria do usuario com esse nome e tipo, ou {@code null} se nenhuma tiver o nome. Nome que
   * so existe com o outro tipo e recusado.
   */
  private Category findCategory(Context context, TransactionType type, String name) {
    String wanted = normalize(name);
    if (wanted.isEmpty()) {
      throw noCompatibleCategory();
    }
    // O prompt mostra o nome cortado (UntrustedText): o modelo pode devolver essa forma.
    List<Category> sameName =
        context.categories().stream()
            .filter(
                category ->
                    normalize(category.getName()).equals(wanted)
                        || normalize(UntrustedText.categoryName(category.getName())).equals(wanted))
            .toList();
    if (sameName.isEmpty()) {
      return null;
    }
    return sameName.stream()
        .filter(category -> category.getType() == type)
        .findFirst()
        .orElseThrow(RegisterTransactionAiAction::noCompatibleCategory);
  }

  /**
   * O nome vem do modelo e vai entrar nos prompts seguintes: so nomes curtos, de poucas palavras e
   * sem pontuacao viram categoria. O guardrail roda no {@link CategoryService}.
   */
  private Category createCategory(Context context, TransactionType type, String name) {
    if (name.length() > MAX_NEW_CATEGORY
        || name.split(" ").length > MAX_NEW_CATEGORY_WORDS
        || !NEW_CATEGORY_NAME.matcher(name).matches()) {
      throw noCompatibleCategory();
    }
    String capitalized = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    return categoryService.createSuggested(context.user(), capitalized, type);
  }

  private static BusinessException noCompatibleCategory() {
    return unprocessable("Nao encontrei uma categoria compativel. Escolha uma manualmente.");
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
