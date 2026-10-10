package br.com.finup.config;

import br.com.finup.repository.ConversationTranscriptStore;
import br.com.finup.repository.InMemoryConversationTranscriptStore;
import br.com.finup.repository.S3ConversationTranscriptStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** Escolhe onde o corpo das conversas e gravado: S3 quando ha bucket, memoria quando nao ha. */
@Configuration
@EnableConfigurationProperties(ChatHistoryProperties.class)
public class ChatHistoryConfig {

  private static final Logger log = LoggerFactory.getLogger(ChatHistoryConfig.class);

  @Bean
  public ConversationTranscriptStore conversationTranscriptStore(
      ChatHistoryProperties properties, BedrockProperties aws, ObjectMapper objectMapper) {
    if (!StringUtils.hasText(properties.bucket())) {
      log.warn(
          "finup.ai.chat-history.bucket vazio: historico de conversas em MEMORIA (some ao"
              + " reiniciar). Configure CHAT_HISTORY_BUCKET fora do desenvolvimento.");
      return new InMemoryConversationTranscriptStore();
    }
    String region = StringUtils.hasText(properties.region()) ? properties.region() : aws.region();
    S3Client s3 =
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(BedrockClientConfig.credentialsProvider(aws))
            .build();
    return new S3ConversationTranscriptStore(
        s3, objectMapper, properties.bucket(), properties.prefix());
  }
}
