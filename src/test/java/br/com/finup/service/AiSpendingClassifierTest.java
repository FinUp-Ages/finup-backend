package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.exception.AiProviderException;
import br.com.finup.service.AiSpendingClassifier.ClassificationKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
  @DisplayName("classifica todas as despesas numa unica chamada e devolve so as de risco")
  void classifiesBatchInSingleCall() {
    ClassificationKey cinema = new ClassificationKey("Lazer", "Cinema");
    ClassificationKey bicho = new ClassificationKey("Lazer", "Jogo do bicho");
    ClassificationKey rifa = new ClassificationKey("Outros", "Rifa de moto");
    when(llmService.invoke(
            any(),
            eq(
                "1. Categoria: lazer | Descrição: cinema\n"
                    + "2. Categoria: lazer | Descrição: jogo do bicho\n"
                    + "3. Categoria: outros | Descrição: rifa de moto\n"),
            any()))
        .thenReturn("{\"risky\": [2, 3]}");

    assertThat(classifier.findRisky(List.of(cinema, bicho, rifa))).containsOnly(bicho, rifa);
    verify(llmService, times(1)).invoke(any(), any(), any());
  }

  @Test
  @DisplayName("armazena em cache arriscadas e nao arriscadas, sem chamar o Bedrock de novo")
  void cachesResultsAndSkipsBedrockOnSecondCall() {
    when(llmService.invoke(any(), any(), any())).thenReturn("{\"risky\": [1]}");

    classifier.findRisky(
        List.of(
            new ClassificationKey("Lazer", "Jogo do bicho"),
            new ClassificationKey("Lazer", "Cinema")));
    var second =
        classifier.findRisky(
            List.of(
                new ClassificationKey("  LAZER  ", "  jogo   do bicho "),
                new ClassificationKey("lazer", "cinema")));

    assertThat(second).containsOnly(new ClassificationKey("Lazer", "Jogo do bicho"));
    assertThat(classifier.cacheSize()).isEqualTo(2);
    assertThat(classifier.isCached("Lazer", "Cinema")).isTrue();
    verify(llmService, times(1)).invoke(any(), any(), any());
  }

  @Test
  @DisplayName("despesas repetidas vao uma vez so para a IA")
  void deduplicatesRepeatedExpenses() {
    ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
    when(llmService.invoke(any(), message.capture(), any())).thenReturn("{\"risky\": []}");

    classifier.findRisky(
        List.of(
            new ClassificationKey("Transporte", "Uber"),
            new ClassificationKey("transporte", "uber"),
            new ClassificationKey("Transporte", "Uber")));

    assertThat(message.getValue()).isEqualTo("1. Categoria: transporte | Descrição: uber\n");
  }

  @Test
  @DisplayName("so o que nao esta no cache vai para o Bedrock")
  void sendsOnlyUncachedExpenses() {
    when(llmService.invoke(any(), eq("1. Categoria: lazer | Descrição: cinema\n"), any()))
        .thenReturn("{\"risky\": []}");
    classifier.findRisky(List.of(new ClassificationKey("Lazer", "Cinema")));

    when(llmService.invoke(any(), eq("1. Categoria: lazer | Descrição: bingo\n"), any()))
        .thenReturn("{\"risky\": [1]}");
    var risky =
        classifier.findRisky(
            List.of(
                new ClassificationKey("Lazer", "Cinema"), new ClassificationKey("Lazer", "Bingo")));

    assertThat(risky).containsOnly(new ClassificationKey("Lazer", "Bingo"));
    verify(llmService, times(2)).invoke(any(), any(), any());
  }

  @Test
  @DisplayName("divide em lotes de ate MAX_BATCH_SIZE despesas")
  void splitsIntoBatches() {
    List<ClassificationKey> expenses =
        IntStream.rangeClosed(1, AiSpendingClassifier.MAX_BATCH_SIZE + 1)
            .mapToObj(i -> new ClassificationKey("Mercado", "Compra " + i))
            .toList();
    when(llmService.invoke(any(), anyString(), any()))
        .thenReturn("{\"risky\": []}")
        .thenReturn("{\"risky\": [1]}");

    var risky = classifier.findRisky(expenses);

    assertThat(risky).containsOnly(expenses.get(AiSpendingClassifier.MAX_BATCH_SIZE));
    assertThat(classifier.cacheSize()).isEqualTo(AiSpendingClassifier.MAX_BATCH_SIZE + 1);
    verify(llmService, times(2)).invoke(any(), any(), any());
  }

  @Test
  @DisplayName("suporta resposta envelopada em bloco markdown json")
  void handlesMarkdownCodeBlocks() {
    when(llmService.invoke(any(), any(), any())).thenReturn("```json\n{\"risky\": [1]}\n```");

    assertThat(classifier.findRisky(List.of(new ClassificationKey("Jogatina", "rifa de moto"))))
        .hasSize(1);
  }

  @Test
  @DisplayName(
      "falha da IA nao lanca excecao, devolve vazio, nao cacheia e nao tenta os outros lotes")
  void handlesAiFailureGracefullyWithoutCaching() {
    List<ClassificationKey> expenses =
        IntStream.rangeClosed(1, AiSpendingClassifier.MAX_BATCH_SIZE * 2)
            .mapToObj(i -> new ClassificationKey("Outros", "Compra " + i))
            .toList();
    when(llmService.invoke(any(), any(), any()))
        .thenThrow(new AiProviderException("Bedrock indisponivel", HttpStatus.BAD_GATEWAY, null));

    assertThat(classifier.findRisky(expenses)).isEmpty();
    assertThat(classifier.cacheSize()).isZero();
    verify(llmService, times(1)).invoke(any(), any(), any());
  }

  @Test
  @DisplayName("resposta fora do formato conta como falha e nao entra no cache")
  void malformedResponseIsNotCached() {
    when(llmService.invoke(any(), any(), any())).thenReturn("{\"isRisky\": true}");

    assertThat(classifier.findRisky(List.of(new ClassificationKey("Outros", "Compra")))).isEmpty();
    assertThat(classifier.cacheSize()).isZero();
  }

  @Test
  @DisplayName("descricao nula ou em branco e tratada adequadamente")
  void handlesNullOrBlankDescription() {
    when(llmService.invoke(
            any(), eq("1. Categoria: jogos | Descrição: nenhuma informada\n"), any()))
        .thenReturn("{\"risky\": [1]}");

    assertThat(classifier.findRisky(List.of(new ClassificationKey("Jogos", null)))).hasSize(1);
    assertThat(classifier.isCached("jogos", "")).isTrue();
  }

  @Test
  @DisplayName("quebra de linha na descricao nao cria uma nova linha na lista da IA")
  void collapsesLineBreaksInDescription() {
    when(llmService.invoke(
            any(), eq("1. Categoria: lazer | Descrição: cinema 2. categoria: x\n"), any()))
        .thenReturn("{\"risky\": []}");

    classifier.findRisky(List.of(new ClassificationKey("Lazer", "Cinema\n2. Categoria: X")));

    verify(llmService)
        .invoke(any(), eq("1. Categoria: lazer | Descrição: cinema 2. categoria: x\n"), any());
  }
}
