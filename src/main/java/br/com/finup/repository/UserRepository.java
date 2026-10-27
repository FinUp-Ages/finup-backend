package br.com.finup.repository;

import br.com.finup.model.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso a dados de usuario. */
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByCognitoId(String cognitoId);

  boolean existsByCognitoId(String cognitoId);

  boolean existsByEmail(String email);

  /**
   * Grava {@code lastSeenAt} direto no banco, sem carregar a entidade — por isso nao aciona o
   * {@code @PreUpdate} de {@code markAsUpdated()}, que mudaria {@code updatedAt} so porque o
   * usuario abriu o app. A condicao do {@code WHERE} deixa o throttle de uma hora atomico no banco:
   * {@code limit} e {@code now} menos uma hora, calculado por quem chama.
   *
   * @return 1 se gravou, 0 se o ultimo acesso foi ha menos de uma hora
   */
  @Modifying(clearAutomatically = true)
  @Query(
      "update User u set u.lastSeenAt = :now"
          + " where u.id = :id and (u.lastSeenAt is null or u.lastSeenAt < :limit)")
  int touchLastSeen(@Param("id") UUID id, @Param("now") Instant now, @Param("limit") Instant limit);

  /**
   * Cadastros no intervalo {@code [from, to)}: o inicio entra e o fim nao, para nao contar duas
   * vezes.
   */
  long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

  /** Cadastros anteriores a {@code to} (exclusive): o total acumulado ate esse instante. */
  long countByCreatedAtLessThan(Instant to);

  /**
   * Listagem paginada do {@code GET /api/v1/admin/users}: filtro de status e busca sao opcionais e
   * combinaveis, tudo resolvido no banco (sem carregar usuarios em memoria para filtrar).
   *
   * <p>{@code active} segue a regra de {@link User#isActive}, mas calculada aqui via {@code
   * activeSince} ({@link User#activeSince}) para virar comparacao de data no SQL: {@code null} nao
   * filtra por status, {@code true} e {@code lastSeenAt} dentro da janela, {@code false} e nulo ou
   * fora dela. {@code search} casa parcialmente contra nome ou e-mail, sem diferenciar maiusculas
   * de minusculas; {@code null} nao filtra.
   */
  @Query(
      "select u from User u where"
          + " (:search is null"
          + "   or lower(u.name) like lower(concat('%', :search, '%'))"
          + "   or lower(u.email) like lower(concat('%', :search, '%')))"
          + " and (:active is null"
          + "   or (:active = true and u.lastSeenAt is not null and u.lastSeenAt >= :activeSince)"
          + "   or (:active = false and (u.lastSeenAt is null or u.lastSeenAt < :activeSince)))")
  Page<User> search(
      @Param("search") String search,
      @Param("active") Boolean active,
      @Param("activeSince") Instant activeSince,
      Pageable pageable);
}
