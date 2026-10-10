package br.com.finup.repository;

import br.com.finup.exception.ConversationStorageException;
import br.com.finup.model.ChatMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Um objeto JSON por conversa em {@code <prefixo>/<userId>/<conversationId>.json}.
 *
 * <p>A chave so tem UUIDs: nada de dado pessoal no nome do objeto. O bucket deve ser privado, com
 * bloqueio de acesso publico — o transcript tem o que o usuario contou sobre as proprias financas.
 * Quem chama serializa as escritas da mesma conversa (lock de linha em {@code
 * ConversationService}), porque ler-alterar-gravar um objeto do S3 nao e atomico.
 */
public class S3ConversationTranscriptStore implements ConversationTranscriptStore {

  private static final Logger log = LoggerFactory.getLogger(S3ConversationTranscriptStore.class);
  private static final TypeReference<List<ChatMessage>> MESSAGES = new TypeReference<>() {};

  private final S3Client s3;
  private final ObjectMapper objectMapper;
  private final String bucket;
  private final String prefix;

  public S3ConversationTranscriptStore(
      S3Client s3, ObjectMapper objectMapper, String bucket, String prefix) {
    this.s3 = s3;
    this.objectMapper = objectMapper;
    this.bucket = bucket;
    this.prefix = prefix;
  }

  @Override
  public List<ChatMessage> read(UUID userId, UUID conversationId) {
    try {
      byte[] json =
          s3.getObjectAsBytes(
                  GetObjectRequest.builder()
                      .bucket(bucket)
                      .key(key(userId, conversationId))
                      .build())
              .asByteArray();
      return objectMapper.readValue(json, MESSAGES);
    } catch (NoSuchKeyException e) {
      return List.of();
    } catch (SdkException | IOException e) {
      log.error("Falha ao ler o transcript: conversationId={}", conversationId, e);
      throw new ConversationStorageException(e);
    }
  }

  @Override
  public void write(UUID userId, UUID conversationId, List<ChatMessage> messages) {
    try {
      byte[] json = objectMapper.writeValueAsBytes(messages);
      s3.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(key(userId, conversationId))
              .contentType("application/json")
              .serverSideEncryption(ServerSideEncryption.AES256)
              .build(),
          RequestBody.fromBytes(json));
    } catch (SdkException | JsonProcessingException e) {
      log.error("Falha ao gravar o transcript: conversationId={}", conversationId, e);
      throw new ConversationStorageException(e);
    }
  }

  String key(UUID userId, UUID conversationId) {
    return "%s/%s/%s.json".formatted(prefix, userId, conversationId);
  }
}
