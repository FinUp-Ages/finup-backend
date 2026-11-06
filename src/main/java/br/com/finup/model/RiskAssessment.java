package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** Avaliação de riscos gerada por um envio da triagem: perfil, resumo e pontos de atenção. */
@Entity
@Table(name = "risk_assessments")
public class RiskAssessment {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "triage_response_id", nullable = false, updatable = false)
  private UUID triageResponseId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50, updatable = false)
  private FinancialProfile profile;

  @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
  private String summary;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false)
  private List<TriageRisk> risks;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected RiskAssessment() {}

  public RiskAssessment(
      UUID userId,
      UUID triageResponseId,
      FinancialProfile profile,
      String summary,
      List<TriageRisk> risks,
      Instant createdAt) {
    this.userId = Objects.requireNonNull(userId);
    this.triageResponseId = Objects.requireNonNull(triageResponseId);
    this.profile = Objects.requireNonNull(profile);
    this.summary = Objects.requireNonNull(summary);
    this.risks = Objects.requireNonNull(risks);
    this.createdAt = Objects.requireNonNull(createdAt);
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getTriageResponseId() {
    return triageResponseId;
  }

  public FinancialProfile getProfile() {
    return profile;
  }

  public String getSummary() {
    return summary;
  }

  public List<TriageRisk> getRisks() {
    return risks;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
