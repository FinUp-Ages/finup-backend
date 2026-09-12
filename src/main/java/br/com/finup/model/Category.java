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
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

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

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  private CategoryType type;

  @Column(name = "is_default")
  private Boolean isDefault;

  @Column(name = "created_at", updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  public Category() {}

  public Category(
      UUID id,
      User user,
      String name,
      CategoryType type,
      Boolean isDefault,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    this.id = id;
    this.user = user;
    this.name = name;
    this.type = type;
    this.isDefault = isDefault;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = OffsetDateTime.now();
    this.updatedAt = OffsetDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = OffsetDateTime.now();
  }

  // Getters
  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getName() {
    return name;
  }

  public CategoryType getType() {
    return type;
  }

  public Boolean getIsDefault() {
    return isDefault;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  // Setters
  public void setId(UUID id) {
    this.id = id;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setType(CategoryType type) {
    this.type = type;
  }

  public void setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
