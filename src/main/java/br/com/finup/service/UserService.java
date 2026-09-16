package br.com.finup.service;

import br.com.finup.exception.ConflictException;
import br.com.finup.exception.EmailAlreadyRegisteredException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.exception.UserAlreadyRegisteredException;
import br.com.finup.model.FinancialProfile;
import br.com.finup.model.User;
import br.com.finup.repository.UserRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
   * <p>As duas checagens abaixo cobrem o caso comum e produzem a mensagem especifica, mas sao um
   * check-then-insert: entre a consulta e o {@code save} outra requisicao pode inserir a mesma
   * identidade. Quem garante a unicidade de fato sao os indices {@code UNIQUE} de {@code
   * cognito_id} e {@code email}; o {@code catch} traduz essa corrida para o mesmo 409 das
   * checagens, em vez do 500 que o handler generico devolveria.
   *
   * @throws UserAlreadyRegisteredException se essa identidade ja tiver um usuario local
   * @throws EmailAlreadyRegisteredException se o e-mail ja pertencer a outro usuario
   * @throws ConflictException se outra requisicao cadastrar a mesma identidade ao mesmo tempo
   */
  @Transactional
  public User createFromAuthenticatedIdentity(AuthenticatedIdentity identity) {
    String normalizedEmail = identity.email().strip().toLowerCase();

    if (userRepository.existsByCognitoId(identity.cognitoId())) {
      throw new UserAlreadyRegisteredException();
    }
    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new EmailAlreadyRegisteredException(identity.email());
    }

    User user;
    try {
      user =
          userRepository.saveAndFlush(
              User.createFromCognitoIdentity(
                  identity.cognitoId(), identity.name(), identity.email()));
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("Ja existe um usuario cadastrado para esta identidade.");
    }
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
  @Transactional
  public User updateAdditionalInfo(
      AuthenticatedIdentity identity,
      LocalDate birthDate,
      BigDecimal monthlyIncome,
      FinancialProfile financialProfile) {
    User user = findByIdentityOrThrow(identity);
    user.applyAdditionalInfo(birthDate, monthlyIncome, financialProfile);
    User updated = userRepository.save(user);
    log.info("Informacoes complementares atualizadas: id={}", updated.getId());
    return updated;
  }

  /**
   * Busca o usuario correspondente a identidade autenticada — usado no login, para o mobile saber
   * se a Etapa 1 ja foi feita.
   *
   * @throws ResourceNotFoundException se essa identidade ainda nao tiver usuario local
   */
  public User findByAuthenticatedIdentity(AuthenticatedIdentity identity) {
    return findByIdentityOrThrow(identity);
  }

  private User findByIdentityOrThrow(AuthenticatedIdentity identity) {
    return userRepository
        .findByCognitoId(identity.cognitoId())
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Usuario nao encontrado para a identidade autenticada."));
  }
}
