package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Um envio do questionário de triagem. Cada envio é uma linha nova: o mais recente vale, os
 * anteriores ficam como histórico. As respostas são dado financeiro pessoal: nunca vão para log.
 */
@Entity
@Table(name = "triage_responses")
public class TriageResponse {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "questionnaire_version", nullable = false, updatable = false)
  private int questionnaireVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false)
  private Map<String, Object> answers;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected TriageResponse() {}

  public TriageResponse(
      UUID userId, int questionnaireVersion, Map<String, Object> answers, Instant createdAt) {
    this.userId = Objects.requireNonNull(userId);
    this.questionnaireVersion = questionnaireVersion;
    this.answers = Objects.requireNonNull(answers);
    this.createdAt = Objects.requireNonNull(createdAt);
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public int getQuestionnaireVersion() {
    return questionnaireVersion;
  }

  public Map<String, Object> getAnswers() {
    return answers;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
