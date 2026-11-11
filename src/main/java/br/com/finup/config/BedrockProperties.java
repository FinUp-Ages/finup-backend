package br.com.finup.config;

import br.com.finup.model.AiModel;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do Amazon Bedrock ({@code finup.ai.bedrock.*}).
 *
 * <p>{@code accessKeyId} e {@code secretAccessKey} sao opcionais: vazios, o SDK usa a cadeia padrao
 * de credenciais da AWS. Nunca versione valores reais — use variaveis de ambiente.
 */
@ConfigurationProperties(prefix = "finup.ai.bedrock")
public record BedrockProperties(
    String region,
    String accessKeyId,
    String secretAccessKey,
    AiModel defaultModel,
    int maxTokens,
    Map<AiModel, String> modelIds,
    Guardrail guardrail) {

  /**
   * Guardrail aplicado a mensagem do usuario (criado em {@code infra/}). Sem {@code id}, fica
   * desligado. Com {@code id}, a versao e obrigatoria: apontar para o DRAFT mudaria o comportamento
   * em producao a cada edicao no console.
   */
  public record Guardrail(String id, String version) {

    public Guardrail {
      if (id != null && !id.isBlank() && (version == null || version.isBlank())) {
        throw new IllegalArgumentException(
            "finup.ai.bedrock.guardrail.version e obrigatoria quando o guardrail tem id");
      }
    }

    public boolean enabled() {
      return id != null && !id.isBlank();
    }
  }
}
