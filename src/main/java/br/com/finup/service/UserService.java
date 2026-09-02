package br.com.finup.service;

import br.com.finup.exception.EmailAlreadyRegisteredException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.User;
import br.com.finup.repository.UserRepository;
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
 * pode ser construida pela metade e o teste passa um duble sem precisar de contexto Spring.
 */
@Service
public class UserService {

  private static final Logger log = LoggerFactory.getLogger(UserService.class);

  private final UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /**
   * Cadastra um usuario.
   *
   * @throws EmailAlreadyRegisteredException se o e-mail ja pertencer a outro usuario
   */
  public User register(String name, String email) {
    if (userRepository.existsByEmail(email)) {
      throw new EmailAlreadyRegisteredException(email);
    }

    User user = userRepository.save(User.register(name, email));
    log.info("Usuario cadastrado: id={}", user.getId());
    return user;
  }

  /**
   * Busca um usuario pelo identificador.
   *
   * @throws ResourceNotFoundException se nao existir usuario com esse id
   */
  public User findById(UUID id) {
    return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
  }

  public List<User> findAll() {
    return userRepository.findAll();
  }
}
