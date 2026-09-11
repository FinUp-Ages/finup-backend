package br.com.finup.repository;

import br.com.finup.model.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de acesso a dados de usuario.
 *
 * <p>O service depende desta interface, nunca da implementacao. E o que permite trocar o
 * armazenamento em memoria por JPA sem tocar em regra de negocio, e testar o service sem banco.
 *
 * <p>Devolve {@link Optional} em vez de {@code null}: quem chama e obrigado a tratar a ausencia.
 */
public interface UserRepository {

  User save(User user);

  Optional<User> findById(UUID id);

  Optional<User> findByCognitoId(String cognitoId);

  boolean existsByCognitoId(String cognitoId);

  boolean existsByEmail(String email);

  List<User> findAll();
}
