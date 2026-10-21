package br.com.finup.repository;

import br.com.finup.model.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
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
}
