package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Pergunta da trilha de estudos (Contrato C6).
 *
 * <p>Guarda o rascunho em {@code draft} e a versão publicada em {@code published} em colunas JSON.
 * O {@code status} é puramente derivado (nunca gravado em coluna).
 */
@Entity
@Table(name = "study_questions")
public class StudyQuestion {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private StudyCategory category;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private QuestionType type;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "draft", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> draft;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "published", columnDefinition = "jsonb")
  private Map<String, Object> published;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected StudyQuestion() {}

  public StudyQuestion(
      UUID id,
      StudyCategory category,
      QuestionType type,
      Map<String, Object> draft,
      Map<String, Object> published,
      Instant publishedAt) {
    this.id = id;
    this.category = Objects.requireNonNull(category, "Categoria é obrigatória");
    this.type = Objects.requireNonNull(type, "Tipo da pergunta é obrigatório");
    this.draft = Objects.requireNonNull(draft, "Draft é obrigatório");
    this.published = published;
    this.publishedAt = publishedAt;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public StudyQuestion(
      StudyCategory category,
      QuestionType type,
      Map<String, Object> draft,
      Map<String, Object> published,
      Instant publishedAt) {
    this(null, category, type, draft, published, publishedAt);
  }

  @PrePersist
  void prePersist() {
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
    this.updatedAt = this.createdAt;
  }

  @PreUpdate
  void preUpdate() {
    this.updatedAt = Instant.now();
  }

  /**
   * Calcula o status derivado da pergunta (Contrato C6):
   *
   * <ul>
   *   <li>{@code DRAFT}: se {@code published} é nulo (nunca foi ao ar).
   *   <li>{@code PUBLISHED}: se publicada e o conteúdo de {@code draft} é igual a {@code
   *       published}.
   *   <li>{@code PUBLISHED_WITH_CHANGES}: se publicada, mas o {@code draft} difere de {@code
   *       published}.
   * </ul>
   */
  @Transient
  public QuestionStatus getStatus() {
    if (published == null) {
      return QuestionStatus.DRAFT;
    }
    if (Objects.equals(draft, published)) {
      return QuestionStatus.PUBLISHED;
    }
    return QuestionStatus.PUBLISHED_WITH_CHANGES;
  }

  public UUID getId() {
    return id;
  }

  public StudyCategory getCategory() {
    return category;
  }

  public void setCategory(StudyCategory category) {
    this.category = Objects.requireNonNull(category, "Categoria é obrigatória");
  }

  public QuestionType getType() {
    return type;
  }

  public void setType(QuestionType type) {
    this.type = Objects.requireNonNull(type, "Tipo é obrigatório");
  }

  public Map<String, Object> getDraft() {
    return draft;
  }

  public void setDraft(Map<String, Object> draft) {
    this.draft = Objects.requireNonNull(draft, "Draft é obrigatório");
  }

  public Map<String, Object> getPublished() {
    return published;
  }

  public void setPublished(Map<String, Object> published) {
    this.published = published;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(Instant publishedAt) {
    this.publishedAt = publishedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    return other instanceof StudyQuestion that && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
