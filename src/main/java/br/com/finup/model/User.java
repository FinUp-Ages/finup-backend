package br.com.finup.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Usuario do FinUp.
 *
 * <p>Entidade de dominio: guarda o estado e as invariantes que valem para qualquer usuario, venha
 * ele da API, de uma carga ou de um teste. O DTO valida o <em>formato</em> da entrada; o que esta
 * aqui vale sempre.
 *
 * <p>Imutavel de proposito: nao ha setter. Mudanca de estado vira metodo com nome de negocio, que
 * devolve uma nova instancia.
 *
 * <p>Quando o banco entrar, esta classe recebe {@code @Entity} e o {@code id} recebe {@code @Id}.
 * Nada aqui muda por causa disso — a entidade nao conhece HTTP nem persistencia.
 */

@Entity
@Table(name = "users")
public class User {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  private String name;
  private String email;

  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  protected User() {}

  private User(UUID id, String name, String email, Instant createdAt) {
    this.id = id;
    this.name = name;
    this.email = email;
    this.createdAt = createdAt;
  }

  /**
   * Cadastra um usuario novo. O identificador e o instante de criacao sao responsabilidade do
   * dominio, nunca do cliente da API.
   */
  public static User register(String name, String email) {
    return new User(UUID.randomUUID(), name.strip(), normalizeEmail(email), Instant.now());
  }

  /** Devolve uma copia com o nome trocado. A instancia original continua valida. */
  public User withName(String newName) {
    return new User(this.id, newName.strip(), this.email, this.createdAt);
  }

  /**
   * E-mail e chave de unicidade: sem normalizar, "Ana@x.com" e "ana@x.com" viram dois cadastros. A
   * regra mora aqui, e nao no service, para valer em qualquer caminho de criacao.
   */
  private static String normalizeEmail(String email) {
    return email.strip().toLowerCase();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Identidade de entidade e o id — dois usuarios com o mesmo id sao o mesmo usuario. */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof User user && Objects.equals(id, user.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "User[id=%s, email=%s]".formatted(id, email);
  }
}
