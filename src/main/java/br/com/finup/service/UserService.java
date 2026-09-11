package br.com.finup.service;

import br.com.finup.exception.EmailAlreadyRegisteredException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.exception.UserAlreadyRegisteredException;
import br.com.finup.model.User;
import br.com.finup.repository.UserRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * negocio; quem traduz para HTTP e o {@code ApiExceptionHandler}.
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
   * Etapa 1 do cadastro: cria o registro local a partir de uma identidade ja autenticada pelo
   * Cognito. Nao recebe nem valida senha — isso e responsabilidade do Cognito.
   *
   * @throws UserAlreadyRegisteredException se essa identidade ja tiver um usuario local
   * @throws EmailAlreadyRegisteredException se o e-mail ja pertencer a outro usuario
   */
  public User createFromAuthenticatedIdentity(AuthenticatedIdentity identity) {
    if (userRepository.existsByCognitoId(identity.cognitoId())) {
      throw new UserAlreadyRegisteredException(identity.cognitoId());
    }
    if (userRepository.existsByEmail(identity.email())) {
      throw new EmailAlreadyRegisteredException(identity.email());
    }

    User user =
        userRepository.save(
            User.createFromCognitoIdentity(
                identity.cognitoId(), identity.name(), identity.email()));
    log.info(
        "Usuario criado a partir do Cognito: id={}, cognitoId={}",
        user.getId(),
        user.getCognitoId());
    return user;
  }

  /**
   * Etapa 2 do cadastro: grava ou atualiza as informacoes complementares de um usuario que ja
   * existe. So pode ser chamada depois da Etapa 1 — nao cria usuario, so complementa um que ja foi
   * criado a partir da identidade do Cognito.
   *
   * @throws ResourceNotFoundException se essa identidade ainda nao tiver usuario local
   */
  public User updateAdditionalInfo(
      AuthenticatedIdentity identity,
      LocalDate birthDate,
      BigDecimal monthlyIncome,
      String financialProfile) {
    User user =
        userRepository
            .findByCognitoId(identity.cognitoId())
            .orElseThrow(() -> new ResourceNotFoundException("User", identity.cognitoId()));

    User updated =
        userRepository.save(user.withAdditionalInfo(birthDate, monthlyIncome, financialProfile));
    log.info("Informacoes complementares atualizadas: id={}", updated.getId());
    return updated;
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
