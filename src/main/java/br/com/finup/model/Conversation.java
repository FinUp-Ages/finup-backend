package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UuidGenerator;

/**
 * Conversa do usuário com o assistente.
 *
 * <p>Cobre o necessário para listar o histórico ({@code GET /api/v1/assistant/conversations}).
 * Criação de conversas e registro de mensagens ainda não existem no sistema, então {@code title} é
 * preenchido por quem implementar essa parte depois. A estratégia de título (recorte da primeira
 * mensagem do usuário vs. resumo curto pedido ao próprio modelo) depende de validação com o time e
 * fica fora do escopo desta feature.
 */
@Entity
@Table(name = "conversations")
public class Conversation {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(length = 255)
  private String title;

  /** Quantas mensagens o transcript no S3 tem. O texto das mensagens nao fica no banco. */
  @ColumnDefault("0")
  @Column(name = "message_count", nullable = false)
  private int messageCount;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Construtor protegido exclusivo para o JPA. */
  protected Conversation() {}

  private Conversation(User user, String title) {
    this.user = Objects.requireNonNull(user, "user é obrigatório");
    this.title = title;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public static Conversation startForUser(User user, String title) {
    return new Conversation(user, title);
  }

  /** Registra mensagens acrescentadas ao transcript; atualiza a data da conversa. */
  public void recordMessages(int added) {
    this.messageCount += added;
  }

  @PreUpdate
  void markAsUpdated() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public int getMessageCount() {
    return messageCount;
  }

  public String getTitle() {
    return title;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof Conversation c && Objects.equals(id, c.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Conversation[id=%s, title=%s]".formatted(id, title);
  }
}
