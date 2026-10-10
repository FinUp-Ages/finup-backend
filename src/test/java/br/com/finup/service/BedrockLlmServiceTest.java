package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.config.BedrockProperties;
import br.com.finup.exception.AiProviderException;
import br.com.finup.model.AiModel;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

/** Verifica o pedido montado para a Converse API e a traducao dos erros do Bedrock. */
@ExtendWith(MockitoExtension.class)
class BedrockLlmServiceTest {

  @Mock private BedrockRuntimeClient client;

  private BedrockLlmService service;

  @BeforeEach
  void setUp() {
    BedrockProperties properties =
        new BedrockProperties(
            "us-east-2",
            "",
            "",
            AiModel.AMAZON_LITE,
            512,
            Map.of(AiModel.AMAZON_LITE, "nova-id", AiModel.ANTHROPIC, "claude-id"));
    service = new BedrockLlmService(client, properties);
  }

  private static ConverseResponse responseWith(String text) {
    return ConverseResponse.builder()
        .output(
            ConverseOutput.builder()
                .message(
                    Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.fromText(text))
                        .build())
                .build())
        .build();
  }

  @Test
  @DisplayName("envia prompt de sistema e mensagem do usuario ao modelo padrao")
  void sendsSystemPromptAndUserMessageToDefaultModel() {
    when(client.converse(any(ConverseRequest.class))).thenReturn(responseWith("  ola  "));

    String result = service.invoke("regras", "gastei 7 reais", null);

    assertThat(result).isEqualTo("ola");
    ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
    verify(client).converse(captor.capture());
    ConverseRequest request = captor.getValue();
    assertThat(request.modelId()).isEqualTo("nova-id");
    assertThat(request.system().get(0).text()).isEqualTo("regras");
    assertThat(request.messages().get(0).content().get(0).text()).isEqualTo("gastei 7 reais");
    assertThat(request.inferenceConfig().maxTokens()).isEqualTo(512);
    assertThat(request.inferenceConfig().temperature()).isZero();
  }

  @Test
  @DisplayName("usa o model id do modelo logico escolhido")
  void usesSelectedModel() {
    when(client.converse(any(ConverseRequest.class))).thenReturn(responseWith("ok"));

    service.invoke("s", "m", AiModel.ANTHROPIC);

    ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
    verify(client).converse(captor.capture());
    assertThat(captor.getValue().modelId()).isEqualTo("claude-id");
  }

  @Test
  @DisplayName("modelo sem model id configurado falha claramente")
  void modelWithoutIdFails() {
    assertThatThrownBy(() -> service.invoke("s", "m", AiModel.GPT))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("resposta sem texto vira 502")
  void emptyResponseBecomes502() {
    when(client.converse(any(ConverseRequest.class)))
        .thenReturn(ConverseResponse.builder().build());

    assertThatThrownBy(() -> service.invoke("s", "m", null))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
  }

  @Test
  @DisplayName("throttling do Bedrock vira 429")
  void throttlingBecomes429() {
    when(client.converse(any(ConverseRequest.class)))
        .thenThrow(ThrottlingException.builder().message("slow down").build());

    assertThatThrownBy(() -> service.invoke("s", "m", null))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
  }

  @Test
  @DisplayName("acesso negado no Bedrock vira 403 sem vazar a mensagem original")
  void accessDeniedBecomes403() {
    when(client.converse(any(ConverseRequest.class)))
        .thenThrow(AccessDeniedException.builder().message("arn:aws:secret").build());

    assertThatThrownBy(() -> service.invoke("s", "m", null))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(e.getMessage()).doesNotContain("arn:aws");
            });
  }
}
