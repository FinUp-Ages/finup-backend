package br.com.finup.repository;

import br.com.finup.model.User;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * Implementacao temporaria em memoria.
 *
 * <p>Existe para que o cadastro rode de ponta a ponta sem banco — os dados somem quando a aplicacao
 * reinicia.
 *
 * <p><strong>Substituir quando o PostgreSQL entrar:</strong> apague esta classe e faca {@link
 * UserRepository} estender {@code JpaRepository<User, UUID>}. Nem o service nem o controller mudam.
 * E esse o motivo de a interface existir.
 */
@Repository
public class InMemoryUserRepository implements UserRepository {

  private final Map<UUID, User> storage = new ConcurrentHashMap<>();

  @Override
  public User save(User user) {
    storage.put(user.getId(), user);
    return user;
  }

  @Override
  public Optional<User> findById(UUID id) {
    return Optional.ofNullable(storage.get(id));
  }

  @Override
  public Optional<User> findByCognitoId(String cognitoId) {
    return storage.values().stream()
        .filter(user -> user.getCognitoId().equals(cognitoId))
        .findFirst();
  }

  @Override
  public boolean existsByCognitoId(String cognitoId) {
    return findByCognitoId(cognitoId).isPresent();
  }

  @Override
  public boolean existsByEmail(String email) {
    return storage.values().stream()
        .anyMatch(user -> user.getEmail().equalsIgnoreCase(email.strip()));
  }

  @Override
  public List<User> findAll() {
    return storage.values().stream().sorted(Comparator.comparing(User::getCreatedAt)).toList();
  }
}
