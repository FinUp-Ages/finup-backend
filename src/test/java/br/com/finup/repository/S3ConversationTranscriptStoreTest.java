package br.com.finup.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.exception.ConversationStorageException;
import br.com.finup.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/** Garante chave sem dado pessoal, formato do objeto e traducao das falhas do S3. */
@ExtendWith(MockitoExtension.class)
class S3ConversationTranscriptStoreTest {

  private final UUID userId = UUID.randomUUID();
  private final UUID conversationId = UUID.randomUUID();
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Mock private S3Client s3;

  private S3ConversationTranscriptStore store;

  @BeforeEach
  void setUp() {
    store = new S3ConversationTranscriptStore(s3, objectMapper, "finup-chat", "conversations");
  }

  @Test
  @DisplayName("grava um objeto JSON criptografado numa chave so com UUIDs")
  void writesEncryptedJsonUnderUuidKey() throws Exception {
    List<ChatMessage> messages =
        List.of(
            new ChatMessage(
                ChatMessage.Role.USER,
                "gastei 7 reais na pucrs",
                null,
                Instant.parse("2026-10-02T10:00:00Z")),
            new ChatMessage(
                ChatMessage.Role.ASSISTANT,
                "Despesa registrada.",
                "REGISTER_TRANSACTION",
                Instant.parse("2026-10-02T10:00:01Z")));

    store.write(userId, conversationId, messages);

    ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
    ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
    verify(s3).putObject(put.capture(), body.capture());
    assertThat(put.getValue().bucket()).isEqualTo("finup-chat");
    assertThat(put.getValue().key())
        .isEqualTo("conversations/%s/%s.json".formatted(userId, conversationId));
    assertThat(put.getValue().contentType()).isEqualTo("application/json");
    assertThat(put.getValue().serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
    String json =
        new String(
            body.getValue().contentStreamProvider().newStream().readAllBytes(),
            StandardCharsets.UTF_8);
    assertThat(json).contains("gastei 7 reais na pucrs").contains("REGISTER_TRANSACTION");
  }

  @Test
  @DisplayName("le o transcript gravado de volta, na mesma ordem")
  void readsTranscriptBack() throws Exception {
    List<ChatMessage> messages =
        List.of(
            new ChatMessage(
                ChatMessage.Role.USER, "oi", null, Instant.parse("2026-10-02T10:00:00Z")),
            new ChatMessage(
                ChatMessage.Role.ASSISTANT,
                "ola",
                "FINANCIAL_FEEDBACK",
                Instant.parse("2026-10-02T10:00:01Z")));
    byte[] json = objectMapper.writeValueAsBytes(messages);
    when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), json));

    assertThat(store.read(userId, conversationId)).isEqualTo(messages);
  }

  @Test
  @DisplayName("objeto inexistente e conversa sem transcript, nao erro")
  void missingObjectIsEmpty() {
    when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("nope").build());

    assertThat(store.read(userId, conversationId)).isEmpty();
  }

  @Test
  @DisplayName("falha do S3 vira ConversationStorageException sem vazar a mensagem original")
  void s3FailureIsTranslated() {
    when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(SdkClientException.create("arn:aws:s3:::segredo"));

    assertThatThrownBy(() -> store.read(userId, conversationId))
        .isInstanceOf(ConversationStorageException.class)
        .hasMessageNotContaining("arn:aws");
  }
}
