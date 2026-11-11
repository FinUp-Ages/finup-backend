package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromptEchoTest {

  private static final String PROMPT =
      """
      Voce e o assistente financeiro do app FinUp. Interprete a mensagem do usuario e responda \
      SOMENTE com um unico objeto JSON.
      Categorias do usuario:
      - "Mercado" (EXPENSE)""";

  @Test
  @DisplayName("trecho do prompt na descricao e detectado, sem importar acento, caixa e pontuacao")
  void detectsPromptExcerpt() {
    assertThat(
            PromptEcho.repeats(
                "Você é o ASSISTENTE financeiro do app FinUp! Interprete...", PROMPT))
        .isTrue();
    assertThat(PromptEcho.repeats("gasto: responda somente com um unico objeto json", PROMPT))
        .isTrue();
  }

  @Test
  @DisplayName("descricao comum, ou que so cita a categoria, nao e eco")
  void ordinaryDescriptionIsNotEcho() {
    assertThat(PromptEcho.repeats("compras no mercado do bairro", PROMPT)).isFalse();
    assertThat(PromptEcho.repeats("Mercado", PROMPT)).isFalse();
    assertThat(PromptEcho.repeats("assistente financeiro do app FinUp", PROMPT))
        .as("5 palavras seguidas ficam abaixo do minimo")
        .isFalse();
    assertThat(PromptEcho.repeats(null, PROMPT)).isFalse();
  }
}
