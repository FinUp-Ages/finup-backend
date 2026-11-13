package br.com.finup.service;

import br.com.finup.model.Category;
import com.fasterxml.jackson.databind.JsonNode;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regras para o nome de categoria que vem da saida do modelo, usadas pelas acoes do assistente que
 * encontram ou criam categorias.
 *
 * <p>O nome criado aqui entra nos prompts seguintes, entao so nomes curtos, de poucas palavras e
 * sem pontuacao viram categoria. O guardrail roda depois, no {@link
 * CategoryService#createSuggested}.
 */
final class AiCategoryNames {

  /** Curto como no prompt ({@link UntrustedText}): sem espaco para um comando completo. */
  static final int MAX_LENGTH = UntrustedText.CATEGORY_NAME_MAX;

  static final int MAX_WORDS = 4;

  /** Letras, numeros, espaco, hifen e "&"; sem pontuacao que separe frases. */
  private static final Pattern ALLOWED = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} &-]*");

  private AiCategoryNames() {}

  /** Nome sugerido, sem as aspas com que aparece no prompt e com espacos colapsados. */
  static String parse(JsonNode node) {
    String text = node.isNull() ? "" : node.asText("");
    return text.replaceAll("^\\s*\"|\"\\s*$", "").replaceAll("\\s+", " ").strip();
  }

  /**
   * Categorias com esse nome, de qualquer tipo, ignorando acento e caixa. O prompt mostra o nome
   * cortado ({@link UntrustedText}), entao o modelo pode devolver essa forma.
   */
  static List<Category> withName(List<Category> categories, String name) {
    String wanted = normalize(name);
    return categories.stream()
        .filter(
            category ->
                normalize(category.getName()).equals(wanted)
                    || normalize(UntrustedText.categoryName(category.getName())).equals(wanted))
        .toList();
  }

  /** Se o nome pode virar categoria nova. */
  static boolean isAcceptable(String name) {
    return !name.isEmpty()
        && name.length() <= MAX_LENGTH
        && name.split(" ").length <= MAX_WORDS
        && ALLOWED.matcher(name).matches();
  }

  /** Inicial maiuscula; o resto como o modelo escreveu ("Cuidados pessoais"). */
  static String capitalize(String name) {
    return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
  }

  /** Ignora acento e caixa: "educacao" casa com "Educação". */
  static String normalize(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .strip()
        .toLowerCase(Locale.ROOT);
  }
}
