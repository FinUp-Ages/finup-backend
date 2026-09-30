package br.com.finup.service;

import br.com.finup.config.BedrockProperties;
import br.com.finup.exception.AiProviderException;
import br.com.finup.exception.BusinessException;
import br.com.finup.model.AiModel;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ModelNotReadyException;
import software.amazon.awssdk.services.bedrockruntime.model.ModelTimeoutException;
import software.amazon.awssdk.services.bedrockruntime.model.ServiceUnavailableException;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

/**
 * Envia um prompt ao Bedrock pela Converse API e devolve o texto. Mesmo desenho da nitra-ai, com
 * dois acrescimos: prompt de sistema (separa instrucao de texto do usuario) e traducao dos erros do
 * SDK para {@link AiProviderException}, que o {@code ApiExceptionHandler} ja sabe responder.
 */
@Service
public class BedrockLlmService {

  private static final Logger log = LoggerFactory.getLogger(BedrockLlmService.class);

  private final BedrockRuntimeClient client;
  private final BedrockProperties properties;

  public BedrockLlmService(BedrockRuntimeClient client, BedrockProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  /**
   * @param model modelo logico; nulo usa o padrao configurado
   * @throws AiProviderException se o Bedrock falhar ou devolver algo sem texto
   */
  public String invoke(String systemPrompt, String userMessage, AiModel model) {
    AiModel selected = model != null ? model : properties.defaultModel();
    String modelId = resolveModelId(selected);

    ConverseRequest request =
        ConverseRequest.builder()
            .modelId(modelId)
            .system(SystemContentBlock.fromText(systemPrompt))
            .messages(
                Message.builder()
                    .role(ConversationRole.USER)
                    .content(ContentBlock.fromText(userMessage))
                    .build())
            .inferenceConfig(
                InferenceConfiguration.builder()
                    .maxTokens(properties.maxTokens())
                    .temperature(0f)
                    .build())
            .build();

    try {
      return extractText(client.converse(request));
    } catch (SdkException e) {
      throw translate(e, selected);
    }
  }

  private String resolveModelId(AiModel model) {
    String modelId = properties.modelIds() == null ? null : properties.modelIds().get(model);
    if (modelId == null || modelId.isBlank()) {
      throw new IllegalStateException("Model ID nao configurado para " + model);
    }
    return modelId;
  }

  private String extractText(ConverseResponse response) {
    if (response.output() == null
        || response.output().message() == null
        || !response.output().message().hasContent()) {
      throw new AiProviderException(
          "O modelo devolveu uma resposta vazia.", HttpStatus.BAD_GATEWAY, null);
    }
    return response.output().message().content().stream()
        .map(ContentBlock::text)
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(text -> !text.isEmpty())
        .reduce((left, right) -> left + "\n" + right)
        .orElseThrow(
            () ->
                new AiProviderException(
                    "O modelo devolveu uma resposta sem texto.", HttpStatus.BAD_GATEWAY, null));
  }

  private BusinessException translate(SdkException e, AiModel model) {
    log.error("Falha ao invocar o Bedrock (model={})", model, e);
    if (e instanceof AccessDeniedException) {
      return new AiProviderException("Acesso negado ao Amazon Bedrock.", HttpStatus.FORBIDDEN, e);
    }
    if (e instanceof ThrottlingException) {
      return new AiProviderException(
          "Limite de requisicoes do Amazon Bedrock excedido.", HttpStatus.TOO_MANY_REQUESTS, e);
    }
    if (e instanceof ModelTimeoutException) {
      return new AiProviderException(
          "O modelo demorou demais para responder.", HttpStatus.GATEWAY_TIMEOUT, e);
    }
    if (e instanceof ServiceUnavailableException || e instanceof ModelNotReadyException) {
      return new AiProviderException(
          "O Amazon Bedrock esta temporariamente indisponivel.", HttpStatus.SERVICE_UNAVAILABLE, e);
    }
    if (e instanceof ValidationException) {
      return new AiProviderException(
          "A requisicao foi rejeitada pelo Amazon Bedrock.", HttpStatus.BAD_GATEWAY, e);
    }
    return new AiProviderException(
        "Falha ao processar a requisicao no Amazon Bedrock.", HttpStatus.BAD_GATEWAY, e);
  }
}
