package br.com.finup.repository;

import br.com.finup.model.Usuario;
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
public interface UsuarioRepository {

  Usuario salvar(Usuario usuario);

  Optional<Usuario> buscarPorId(UUID id);

  boolean existePorEmail(String email);

  List<Usuario> listarTodos();
}
