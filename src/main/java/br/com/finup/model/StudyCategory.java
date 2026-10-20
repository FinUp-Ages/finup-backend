package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Categoria de uma trilha de estudos (Contrato C6).
 *
 * <p>Agrupa perguntas e expõe contagens agregadas de itens em rascunho e publicados.
 */
@Entity
@Table(name = "study_categories")
public class StudyCategory {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 255)
  private String description;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected StudyCategory() {}

  public StudyCategory(UUID id, String name, String description) {
    this.id = id;
    this.name = Objects.requireNonNull(name, "Nome é obrigatório").strip();
    this.description = description != null ? description.strip() : null;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public StudyCategory(String name, String description) {
    this(null, name, description);
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

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = Objects.requireNonNull(name, "Nome é obrigatório").strip();
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description != null ? description.strip() : null;
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
    return other instanceof StudyCategory that && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
