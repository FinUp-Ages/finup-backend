package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_methods")
public class PaymentMethod {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "type", length = 50)
  private String type;

  @Column(name = "name", length = 255)
  private String name;

  @Column(name = "institution", length = 255)
  private String institution;

  @Column(name = "closing_day")
  private Integer closingDay;

  @Column(name = "due_day")
  private Integer dueDay;

  @Column(name = "credit_limit", precision = 12, scale = 2)
  private BigDecimal creditLimit;

  @Column(name = "is_active")
  private Boolean isActive = true;

  @Column(name = "created_at", updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = OffsetDateTime.now();
  }

  public PaymentMethod() {}

  public PaymentMethod(
      UUID userId,
      String type,
      String name,
      String institution,
      Integer closingDay,
      Integer dueDay,
      BigDecimal creditLimit,
      Boolean isActive) {
    this.userId = userId;
    this.type = type;
    this.name = name;
    this.institution = institution;
    this.closingDay = closingDay;
    this.dueDay = dueDay;
    this.creditLimit = creditLimit;
    this.isActive = isActive;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public String getInstitution() {
    return institution;
  }

  public Integer getClosingDay() {
    return closingDay;
  }

  public Integer getDueDay() {
    return dueDay;
  }

  public BigDecimal getCreditLimit() {
    return creditLimit;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
