package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.exception.AiProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AiSpendingClassifierTest {

  @Mock private BedrockLlmService llmService;

  private AiSpendingClassifier classifier;

  @BeforeEach
  void setUp() {
    classifier = new AiSpendingClassifier(llmService, new ObjectMapper());
  }

  @Test
  @DisplayName("classifica como arriscado quando IA devolve isRisky true e armazena em cache")
  void classifiesRiskyAndCachesResult() {
    when(llmService.invoke(any(), eq("Categoria: Lazer\nDescrição: Jogo do bicho"), any()))
        .thenReturn("{\"isRisky\": true}");

    boolean firstCall = classifier.isRisky("Lazer", "Jogo do bicho");
    boolean secondCall = classifier.isRisky("lazer", "jogo do bicho");

    assertThat(firstCall).isTrue();
    assertThat(secondCall).isTrue();
    assertThat(classifier.cacheSize()).isEqualTo(1);
    assertThat(classifier.isCached("Lazer", "Jogo do bicho")).isTrue();

    // Bedrock so deve ser chamado uma unica vez por conta do cache
    verify(llmService, times(1)).invoke(any(), any(), any());
  }

  @Test
  @DisplayName("classifica como nao arriscado quando IA devolve isRisky false e armazena em cache")
  void classifiesNonRiskyAndCachesResult() {
    when(llmService.invoke(any(), eq("Categoria: Lazer\nDescrição: Cinema"), any()))
        .thenReturn("{\"isRisky\": false}");

    boolean firstCall = classifier.isRisky("Lazer", "Cinema");
    boolean secondCall = classifier.isRisky("  LAZER  ", "  cinema  ");

    assertThat(firstCall).isFalse();
    assertThat(secondCall).isFalse();
    assertThat(classifier.cacheSize()).isEqualTo(1);
    verify(llmService, times(1)).invoke(any(), any(), any());
  }

  @Test
  @DisplayName("suporta resposta envelopada em bloco markdown json")
  void handlesMarkdownCodeBlocks() {
    when(llmService.invoke(any(), any(), any())).thenReturn("```json\n{\"isRisky\": true}\n```");

    boolean result = classifier.isRisky("Jogatina", "rifa de moto");

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("falha da IA nao lanca excecao, degrada para false e nao armazena erro no cache")
  void handlesAiFailureGracefullyWithoutCaching() {
    when(llmService.invoke(any(), any(), any()))
        .thenThrow(new AiProviderException("Bedrock indisponivel", HttpStatus.BAD_GATEWAY, null));

    boolean result = classifier.isRisky("Outros", "compra misteriosa");

    assertThat(result).isFalse();
    assertThat(classifier.cacheSize()).isZero();
    assertThat(classifier.isCached("Outros", "compra misteriosa")).isFalse();
  }

  @Test
  @DisplayName("descricao nula ou em branco e tratada adequadamente")
  void handlesNullOrBlankDescription() {
    when(llmService.invoke(any(), eq("Categoria: Jogos\nDescrição: nenhuma informada"), any()))
        .thenReturn("{\"isRisky\": true}");

    boolean result = classifier.isRisky("Jogos", null);

    assertThat(result).isTrue();
    assertThat(classifier.isCached("jogos", "")).isTrue();
  }
}
