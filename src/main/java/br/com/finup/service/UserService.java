package br.com.finup.service;

import br.com.finup.exception.ConflictException;
import br.com.finup.exception.EmailAlreadyRegisteredException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.exception.UserAlreadyRegisteredException;
import br.com.finup.model.User;
import br.com.finup.model.UserFinancialProfile;
import br.com.finup.repository.UserFinancialProfileRepository;
import br.com.finup.repository.UserRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
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
  private final UserFinancialProfileRepository userFinancialProfileRepository;

  public UserService(
      UserRepository userRepository,
      UserFinancialProfileRepository userFinancialProfileRepository) {
    this.userRepository = userRepository;
    this.userFinancialProfileRepository = userFinancialProfileRepository;
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
    String normalizedEmail = normalizeEmail(identity.email());

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
   * Etapas 2 e 3 do cadastro: grava ou atualiza as informacoes complementares em {@code
   * user_financial_profiles}, criando o perfil na primeira chamada. So pode ser chamada depois da
   * Etapa 1 — nao cria usuario. Parametro {@code null} mantem o valor ja salvo (PATCH parcial).
   *
   * @throws ResourceNotFoundException se essa identidade ainda nao tiver usuario local
   */
  @Transactional
  public UserFinancialProfile updateAdditionalInfo(
      AuthenticatedIdentity identity,
      String phone,
      String profession,
      LocalDate birthDate,
      BigDecimal monthlyIncome) {
    User user = findByIdentityOrThrow(identity);
    UserFinancialProfile profile =
        userFinancialProfileRepository
            .findByUserId(user.getId())
            .orElseGet(() -> UserFinancialProfile.createFor(user));
    profile.apply(phone, profession, birthDate, monthlyIncome);
    UserFinancialProfile saved = userFinancialProfileRepository.save(profile);
    log.info("Informacoes complementares atualizadas: userId={}", user.getId());
    return saved;
  }

  /** Informacoes complementares do usuario, ou vazio se ele ainda nao informou nenhuma. */
  public Optional<UserFinancialProfile> findFinancialProfile(User user) {
    return userFinancialProfileRepository.findByUserId(user.getId());
  }

  /**
   * Busca o usuario por id, desde que seja o da identidade autenticada. Id de outra pessoa da o
   * mesmo 404 de id inexistente: um 403 confirmaria que o id existe.
   *
   * @throws ResourceNotFoundException se o id nao for do usuario autenticado
   */
  public User findByIdForAuthenticatedIdentity(AuthenticatedIdentity identity, UUID id) {
    User user = findByIdentityOrThrow(identity);
    if (!user.getId().equals(id)) {
      throw new ResourceNotFoundException("Usuario nao encontrado.");
    }
    return user;
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

  /**
   * Diz se nenhum usuario usa o e-mail — usado pelo formulario de cadastro, antes de a pessoa ter
   * conta. Compara do mesmo jeito que {@link #createFromAuthenticatedIdentity}: sem espacos nas
   * pontas e em minusculas, que e como o e-mail e gravado.
   *
   * <p>E so um aviso, nao uma reserva: outra pessoa pode cadastrar o mesmo e-mail logo depois da
   * consulta. Quem garante a unicidade continua sendo o indice {@code UNIQUE} de {@code email}. So
   * enxerga a tabela {@code users}: uma conta que existe no Cognito e ainda nao chegou aqui aparece
   * como disponivel.
   */
  public boolean isEmailAvailable(String email) {
    return !userRepository.existsByEmail(normalizeEmail(email));
  }

  private static String normalizeEmail(String email) {
    return email.strip().toLowerCase();
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
