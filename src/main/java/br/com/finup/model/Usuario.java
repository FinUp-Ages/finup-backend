package br.com.finup.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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
public class Usuario {

  private final UUID id;
  private final String nome;
  private final String email;
  private final Instant criadoEm;

  private Usuario(UUID id, String nome, String email, Instant criadoEm) {
    this.id = id;
    this.nome = nome;
    this.email = email;
    this.criadoEm = criadoEm;
  }

  /**
   * Cadastra um usuario novo. O identificador e o instante de criacao sao responsabilidade do
   * dominio, nunca do cliente da API.
   */
  public static Usuario cadastrar(String nome, String email) {
    return new Usuario(UUID.randomUUID(), nome.strip(), normalizarEmail(email), Instant.now());
  }

  /** Devolve uma copia com o nome trocado. A instancia original continua valida. */
  public Usuario comNome(String novoNome) {
    return new Usuario(this.id, novoNome.strip(), this.email, this.criadoEm);
  }

  /**
   * E-mail e chave de unicidade: sem normalizar, "Ana@x.com" e "ana@x.com" viram dois cadastros. A
   * regra mora aqui, e nao no service, para valer em qualquer caminho de criacao.
   */
  private static String normalizarEmail(String email) {
    return email.strip().toLowerCase();
  }

  public UUID getId() {
    return id;
  }

  public String getNome() {
    return nome;
  }

  public String getEmail() {
    return email;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  /** Identidade de entidade e o id — dois usuarios com o mesmo id sao o mesmo usuario. */
  @Override
  public boolean equals(Object outro) {
    if (this == outro) {
      return true;
    }
    return outro instanceof Usuario usuario && Objects.equals(id, usuario.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Usuario[id=%s, email=%s]".formatted(id, email);
  }
}
