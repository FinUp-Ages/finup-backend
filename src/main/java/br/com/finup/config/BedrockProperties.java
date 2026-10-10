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
    Map<AiModel, String> modelIds) {}
