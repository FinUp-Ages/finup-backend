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
import org.hibernate.annotations.UuidGenerator;

/**
 * Configuração de um widget do dashboard para um admin: se está ligado e em que posição aparece.
 *
 * <p>Um registro por par (usuário, widget). O conjunto de registros do usuário é a configuração
 * inteira; o {@code PUT} troca todos de uma vez.
 */
@Entity
@Table(name = "dashboard_widget_settings")
public class DashboardWidgetSetting {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private User user;

  @Column(name = "widget_id", nullable = false, length = 100)
  private String widgetId;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false)
  private int position;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Construtor protegido exclusivo para o JPA. */
  protected DashboardWidgetSetting() {}

  private DashboardWidgetSetting(User user, String widgetId, boolean enabled, int position) {
    this.user = Objects.requireNonNull(user, "user é obrigatório");
    this.widgetId = Objects.requireNonNull(widgetId, "widgetId é obrigatório").strip();
    this.enabled = enabled;
    this.position = position;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public static DashboardWidgetSetting create(
      User user, String widgetId, boolean enabled, int position) {
    return new DashboardWidgetSetting(user, widgetId, enabled, position);
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

  public String getWidgetId() {
    return widgetId;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public int getPosition() {
    return position;
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
    return other instanceof DashboardWidgetSetting w && Objects.equals(id, w.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "DashboardWidgetSetting[widgetId=%s, enabled=%s, position=%d]"
        .formatted(widgetId, enabled, position);
  }
}
