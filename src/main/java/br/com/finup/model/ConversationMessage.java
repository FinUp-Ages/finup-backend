package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Mensagem de uma {@link Conversation}, enviada pelo usuário ou respondida pelo assistente.
 *
 * <p>Cobre o necessário para carregar uma conversa do histórico ({@code GET
 * /api/v1/assistant/conversations/{id}/messages}). A gravação das mensagens fica com quem
 * implementar o envio ao assistente ({@code POST /api/v1/assistant/messages}). Mensagens não são
 * editadas: não há {@code updated_at}.
 */
@Entity
@Table(name = "conversation_messages")
public class ConversationMessage {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
  private Conversation conversation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  private MessageRole role;

  @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;

  /** Construtor protegido exclusivo para o JPA. */
  protected ConversationMessage() {}

  private ConversationMessage(Conversation conversation, MessageRole role, String content) {
    this.conversation = Objects.requireNonNull(conversation, "conversation é obrigatório");
    this.role = Objects.requireNonNull(role, "role é obrigatório");
    this.content = Objects.requireNonNull(content, "content é obrigatório");
    this.createdAt = Instant.now();
  }

  public static ConversationMessage of(
      Conversation conversation, MessageRole role, String content) {
    return new ConversationMessage(conversation, role, content);
  }

  public UUID getId() {
    return id;
  }

  public Conversation getConversation() {
    return conversation;
  }

  public MessageRole getRole() {
    return role;
  }

  public String getContent() {
    return content;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof ConversationMessage m && Objects.equals(id, m.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "ConversationMessage[id=%s, role=%s]".formatted(id, role);
  }
}
