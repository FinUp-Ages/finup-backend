package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Categoria de transação pertencente a um usuário ou do sistema (padrão).
 *
 * <p>Categorias com {@code isDefault = true} pertencem ao sistema: não têm {@code user} associado e
 * não podem ser editadas nem removidas. Cada usuário só enxerga as próprias categorias somadas às
 * categorias padrão.
 */
@Entity
@Table(name = "categories")
public class Category {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(nullable = false, length = 255)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private TransactionType type;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Construtor protegido exclusivo para o JPA. */
  protected Category() {}

  private Category(User user, String name, TransactionType type, boolean isDefault) {
    this.user = user;
    this.name = Objects.requireNonNull(name, "name é obrigatório").strip();
    this.type = Objects.requireNonNull(type, "type é obrigatório");
    this.isDefault = isDefault;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  /**
   * Cria uma categoria pessoal vinculada a um usuário. Categorias criadas por este método nunca são
   * padrão do sistema.
   */
  public static Category createForUser(User user, String name, TransactionType type) {
    Objects.requireNonNull(user, "user é obrigatório");
    return new Category(user, name, type, false);
  }

  /**
   * Altera nome e tipo da categoria. Chamar em categorias padrão é proibido pelo service antes de
   * chegar aqui.
   */
  public void rename(String name, TransactionType type) {
    this.name = Objects.requireNonNull(name, "name é obrigatório").strip();
    this.type = Objects.requireNonNull(type, "type é obrigatório");
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

  public String getName() {
    return name;
  }

  public TransactionType getType() {
    return type;
  }

  public boolean isDefault() {
    return isDefault;
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
    return other instanceof Category c && Objects.equals(id, c.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Category[id=%s, name=%s, isDefault=%s]".formatted(id, name, isDefault);
  }
}
