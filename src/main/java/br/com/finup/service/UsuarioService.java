package br.com.finup.service;

import br.com.finup.exception.EmailJaCadastradoException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.Usuario;
import br.com.finup.repository.UsuarioRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Regra de negocio de usuario.
 *
 * <p>Repare no que <strong>nao</strong> tem aqui: nenhum {@code ResponseEntity}, nenhum codigo de
 * status, nenhum {@code HttpServletRequest}. O service sinaliza o problema lancando excecao de
 * negocio; quem traduz para HTTP e o {@code ApiExceptionHandler}. E isso que permite reusar este
 * service em um job agendado ou num consumidor de fila sem arrastar a camada web junto.
 *
 * <p>Injecao por construtor, com campo {@code final}: a dependencia fica explicita, a classe nao
 * pode ser construida pela metade e o teste passa um dublê sem precisar de contexto Spring.
 */
@Service
public class UsuarioService {

  private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

  private final UsuarioRepository usuarioRepository;

  public UsuarioService(UsuarioRepository usuarioRepository) {
    this.usuarioRepository = usuarioRepository;
  }

  /**
   * Cadastra um usuario.
   *
   * @throws EmailJaCadastradoException se o e-mail ja pertencer a outro usuario
   */
  public Usuario cadastrar(String nome, String email) {
    if (usuarioRepository.existePorEmail(email)) {
      throw new EmailJaCadastradoException(email);
    }

    Usuario usuario = usuarioRepository.salvar(Usuario.cadastrar(nome, email));
    log.info("Usuario cadastrado: id={}", usuario.getId());
    return usuario;
  }

  /**
   * Busca um usuario pelo identificador.
   *
   * @throws ResourceNotFoundException se nao existir usuario com esse id
   */
  public Usuario buscarPorId(UUID id) {
    return usuarioRepository
        .buscarPorId(id)
        .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
  }

  public List<Usuario> listar() {
    return usuarioRepository.listarTodos();
  }
}
