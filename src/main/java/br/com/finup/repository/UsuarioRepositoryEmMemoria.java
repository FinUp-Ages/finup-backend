package br.com.finup.repository;

import br.com.finup.model.Usuario;
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
 * <p>Existe para que o exemplo de cadastro rode de ponta a ponta sem banco — os dados somem quando
 * a aplicacao reinicia.
 *
 * <p><strong>Substituir quando o PostgreSQL entrar:</strong> apague esta classe e faca
 * {@link UsuarioRepository} estender {@code JpaRepository<Usuario, UUID>}. Nem o service nem o
 * controller mudam. E esse o motivo de a interface existir.
 */
@Repository
public class UsuarioRepositoryEmMemoria implements UsuarioRepository {

  private final Map<UUID, Usuario> armazenamento = new ConcurrentHashMap<>();

  @Override
  public Usuario salvar(Usuario usuario) {
    armazenamento.put(usuario.getId(), usuario);
    return usuario;
  }

  @Override
  public Optional<Usuario> buscarPorId(UUID id) {
    return Optional.ofNullable(armazenamento.get(id));
  }

  @Override
  public boolean existePorEmail(String email) {
    return armazenamento.values().stream()
        .anyMatch(usuario -> usuario.getEmail().equalsIgnoreCase(email.strip()));
  }

  @Override
  public List<Usuario> listarTodos() {
    return armazenamento.values().stream()
        .sorted(Comparator.comparing(Usuario::getCriadoEm))
        .toList();
  }
}
