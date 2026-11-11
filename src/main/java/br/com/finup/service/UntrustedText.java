package br.com.finup.service;

/**
 * Prepara texto digitado pelo usuario (ex.: nome de categoria) para entrar num prompt como dado.
 *
 * <p>Duas defesas contra instrucoes plantadas nesse texto: o corte em poucos caracteres, que tira o
 * espaco para um comando completo, e as aspas de string JSON, que deixam claro ao modelo onde o
 * dado comeca e termina. O prompt que usa estes valores deve dizer que o que esta entre aspas e
 * dado, nunca instrucao.
 */
public final class UntrustedText {

  /** Os nomes legitimos cabem com folga; um comando completo, nao. */
  public static final int CATEGORY_NAME_MAX = 40;

  private UntrustedText() {}

  /** Nome de categoria com espacos colapsados e cortado em {@value #CATEGORY_NAME_MAX}. */
  public static String categoryName(String name) {
    String text = name == null ? "" : name.replaceAll("\\s+", " ").strip();
    return text.length() > CATEGORY_NAME_MAX ? text.substring(0, CATEGORY_NAME_MAX).strip() : text;
  }

  /** {@link #categoryName} entre aspas, como string JSON. */
  public static String quotedCategoryName(String name) {
    return quote(categoryName(name));
  }

  static String quote(String text) {
    StringBuilder quoted = new StringBuilder(text.length() + 2).append('"');
    for (char c : text.toCharArray()) {
      switch (c) {
        case '"' -> quoted.append("\\\"");
        case '\\' -> quoted.append("\\\\");
        default -> {
          if (c < 0x20) {
            quoted.append(' ');
          } else {
            quoted.append(c);
          }
        }
      }
    }
    return quoted.append('"').toString();
  }
}
