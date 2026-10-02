package br.com.finup.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Gastos de risco que o feedback do assistente nunca deixa passar: apostas, bebidas alcoolicas e
 * drogas. Sao tratados como evitaveis.
 *
 * <p>A identificacao e pelo NOME da categoria — decisao deliberada, para nao alterar a tabela
 * {@code categories} enquanto o cadastro de categorias esta em andamento. O limite e conhecido: uma
 * categoria criada pelo usuario com outro nome (ex.: "Jogatina") nao e reconhecida. Quando a
 * categoria ganhar um marcador proprio, so este enum muda.
 */
public enum RiskySpending {
  BETS("Apostas/bets", "\\bbets?\\b", "\\bapostas?\\b", "\\bcassino\\b", "jogos? de azar"),
  ALCOHOL("Bebidas alcoolicas", "bebidas? alcoolicas?", "\\balcool\\b", "\\bcervejas?\\b"),
  DRUGS("Drogas", "\\bdrogas?\\b", "\\bentorpecentes?\\b");

  private final String label;
  private final Pattern pattern;

  RiskySpending(String label, String... regexes) {
    this.label = label;
    this.pattern = Pattern.compile(String.join("|", regexes));
  }

  public String label() {
    return label;
  }

  /** Tipo de gasto de risco a que o nome da categoria corresponde, se algum. */
  public static Optional<RiskySpending> fromCategoryName(String categoryName) {
    if (categoryName == null) {
      return Optional.empty();
    }
    String normalized =
        Normalizer.normalize(categoryName, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    return Arrays.stream(values())
        .filter(kind -> kind.pattern.matcher(normalized).find())
        .findFirst();
  }
}
