package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Registro de que um usuário dispensou o alerta de gastos de uma categoria. Há no máximo um por
 * usuário e categoria; dispensar de novo apenas atualiza o instante.
 */
@Entity
@Table(
    name = "spending_alert_dismissals",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_spending_alert_dismissals_user_category",
            columnNames = {"user_id", "category_id"}))
public class SpendingAlertDismissal {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "category_id", nullable = false, updatable = false)
  private UUID categoryId;

  @Column(name = "dismissed_at", nullable = false)
  private Instant dismissedAt;

  protected SpendingAlertDismissal() {}

  private SpendingAlertDismissal(UUID userId, UUID categoryId, Instant dismissedAt) {
    this.userId = Objects.requireNonNull(userId, "userId é obrigatório");
    this.categoryId = Objects.requireNonNull(categoryId, "categoryId é obrigatório");
    this.dismissedAt = Objects.requireNonNull(dismissedAt, "dismissedAt é obrigatório");
  }

  public static SpendingAlertDismissal dismiss(UUID userId, UUID categoryId, Instant at) {
    return new SpendingAlertDismissal(userId, categoryId, at);
  }

  /** Dispensa de novo uma categoria já dispensada: só renova o instante. */
  public void dismissAgain(Instant at) {
    this.dismissedAt = Objects.requireNonNull(at, "dismissedAt é obrigatório");
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public Instant getDismissedAt() {
    return dismissedAt;
  }
}
