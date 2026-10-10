package br.com.finup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Onde ficam os transcripts das conversas ({@code finup.ai.chat-history.*}).
 *
 * <p>{@code bucket} vazio desliga o S3 e usa armazenamento em memoria (so desenvolvimento). {@code
 * region} vazia herda a regiao do Bedrock.
 */
@ConfigurationProperties(prefix = "finup.ai.chat-history")
public record ChatHistoryProperties(
    String bucket, String region, @DefaultValue("conversations") String prefix) {}
