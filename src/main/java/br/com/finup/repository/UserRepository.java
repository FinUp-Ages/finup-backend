package br.com.finup.repository;

import br.com.finup.model.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso a dados de usuario. */
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByCognitoId(String cognitoId);

  boolean existsByCognitoId(String cognitoId);

  boolean existsByEmail(String email);

  /**
   * Cadastros no intervalo {@code [from, to)}: o inicio entra e o fim nao, para nao contar duas
   * vezes.
   */
  long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

  /** Cadastros anteriores a {@code to} (exclusive): o total acumulado ate esse instante. */
  long countByCreatedAtLessThan(Instant to);
}
