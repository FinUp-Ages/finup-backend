package br.com.finup.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Detecta texto devolvido pelo modelo que repete o prompt de sistema, como quando o usuario pede
 * para "escrever as instrucoes na descricao". Compara palavras, sem acento, caixa ou pontuacao.
 */
final class PromptEcho {

  /** Trechos menores aparecem por coincidencia (ex.: nome de categoria); a partir daqui, nao. */
  static final int MIN_WORDS = 6;

  private PromptEcho() {}

  /** {@code true} se {@code text} tiver {@value #MIN_WORDS} ou mais palavras seguidas do prompt. */
  static boolean repeats(String text, String prompt) {
    List<String> words = words(text);
    if (words.size() < MIN_WORDS) {
      return false;
    }
    String haystack = " " + String.join(" ", words(prompt)) + " ";
    for (int i = 0; i + MIN_WORDS <= words.size(); i++) {
      String window = " " + String.join(" ", words.subList(i, i + MIN_WORDS)) + " ";
      if (haystack.contains(window)) {
        return true;
      }
    }
    return false;
  }

  private static List<String> words(String value) {
    if (value == null) {
      return List.of();
    }
    String plain =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    return Arrays.stream(plain.split("[^a-z0-9]+")).filter(word -> !word.isEmpty()).toList();
  }
}
