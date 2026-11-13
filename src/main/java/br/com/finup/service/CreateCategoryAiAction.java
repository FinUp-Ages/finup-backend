package br.com.finup.service;

import br.com.finup.exception.BusinessException;
import br.com.finup.model.Category;
import br.com.finup.model.TransactionType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * "Cria a categoria Automotivo" vira uma categoria de despesa do usuario, sem registrar transacao.
 *
 * <p>Mesmas regras de nome do registro de transacao ({@link AiCategoryNames}) e mesmo guardrail do
 * cadastro manual. Nao duplica: se ja existe categoria com o nome (ignorando acento e caixa), avisa
 * e nao cria.
 */
@Component
public class CreateCategoryAiAction implements AiAction {

  public static final String NAME = "CREATE_CATEGORY";

  private final CategoryService categoryService;

  public CreateCategoryAiAction(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String promptInstructions() {
    return """
        %s: o usuario pede para criar, adicionar ou cadastrar uma categoria, sem relatar um gasto \
        ou ganho com valor. Ex.: "cria a categoria Automotivo", "quero uma categoria de receita \
        chamada Aluguel".
        Formato: {"action":"%s","type":"EXPENSE|INCOME","category":"<nome pedido>"}
        "type" e INCOME so se o usuario disser que e de receita/ganho; senao EXPENSE. Use o nome \
        que o usuario pediu, em portugues do Brasil com a acentuacao correta e inicial maiuscula."""
        .formatted(NAME, NAME);
  }

  @Override
  public Result execute(Context context, JsonNode payload) {
    TransactionType type = parseType(payload.path("type"));
    String name = AiCategoryNames.parse(payload.path("category"));
    if (name.isEmpty()) {
      throw new BusinessException(
          "Nao consegui identificar o nome da categoria. Tente algo como \"cria a categoria"
              + " Mercado\".");
    }

    List<Category> sameName = AiCategoryNames.withName(context.categories(), name);
    for (Category existing : sameName) {
      if (existing.getType() == type) {
        return new Result(
            "A categoria %s ja existe; nao criei outra.".formatted(existing.getName()), null);
      }
    }
    if (!sameName.isEmpty()) {
      throw new BusinessException(
          "Ja existe a categoria %s de %s. Escolha outro nome."
              .formatted(sameName.get(0).getName(), label(sameName.get(0).getType())));
    }
    if (!AiCategoryNames.isAcceptable(name)) {
      throw new BusinessException(CategoryService.BLOCKED_NAME_MESSAGE);
    }

    Category created =
        categoryService.createSuggested(context.user(), AiCategoryNames.capitalize(name), type);
    return new Result("Categoria de %s %s criada.".formatted(label(type), created.getName()), null);
  }

  /** Sem tipo valido, despesa: e o caso comum e o prompt manda assim. */
  private static TransactionType parseType(JsonNode node) {
    try {
      return TransactionType.valueOf(node.asText("").trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return TransactionType.EXPENSE;
    }
  }

  private static String label(TransactionType type) {
    return type == TransactionType.INCOME ? "receita" : "despesa";
  }
}
