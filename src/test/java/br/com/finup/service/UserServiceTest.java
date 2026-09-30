package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Testes unitarios de service, sem contexto Spring e sem banco: o repositorio e um duble. Roda em
 * milissegundos e falha por um motivo so — a regra de negocio.
 *
 * <p>Nome do metodo em ingles, {@code @DisplayName} em portugues: o relatorio de teste e para o
 * time ler.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;

  @Mock private UserFinancialProfileRepository userFinancialProfileRepository;

  @InjectMocks private UserService userService;

  @Test
  @DisplayName("cria usuario quando a identidade do Cognito ainda nao tem registro local")
  void createsWhenIdentityIsNew() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    when(userRepository.existsByCognitoId("cognito-sub-123")).thenReturn(false);
    when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(false);
    when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    User user = userService.createFromAuthenticatedIdentity(identity);

    assertThat(user.getId()).isNotNull();
    assertThat(user.getCognitoId()).isEqualTo("cognito-sub-123");
    assertThat(user.getName()).isEqualTo("Ana Souza");
    assertThat(user.getEmail()).isEqualTo("ana@exemplo.com");
    assertThat(user.getCreatedAt()).isNotNull();
    assertThat(user.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("permite identidade sem nome, como no login com Apple sem o primeiro acesso")
  void createsWhenNameIsAbsent() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", null, "ana@exemplo.com");
    when(userRepository.existsByCognitoId("cognito-sub-123")).thenReturn(false);
    when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(false);
    when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    User user = userService.createFromAuthenticatedIdentity(identity);

    assertThat(user.getName()).isNull();
  }

  @Test
  @DisplayName("cadastro simultaneo da mesma identidade vira 409, e nao 500")
  void translatesConcurrentInsertIntoConflict() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    when(userRepository.existsByCognitoId("cognito-sub-123")).thenReturn(false);
    when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(false);
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("uk_users_cognito_id"));

    assertThatThrownBy(() -> userService.createFromAuthenticatedIdentity(identity))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("recusa criacao quando a identidade ja tem usuario local e nao chega a gravar")
  void rejectsDuplicateCognitoIdentity() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    when(userRepository.existsByCognitoId("cognito-sub-123")).thenReturn(true);

    assertThatThrownBy(() -> userService.createFromAuthenticatedIdentity(identity))
        .isInstanceOf(UserAlreadyRegisteredException.class);

    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("recusa criacao quando o e-mail ja pertence a outra identidade")
  void rejectsEmailAlreadyUsedByAnotherIdentity() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-456", "Outra Ana", "ana@exemplo.com");
    when(userRepository.existsByCognitoId("cognito-sub-456")).thenReturn(false);
    when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(true);

    assertThatThrownBy(() -> userService.createFromAuthenticatedIdentity(identity))
        .isInstanceOf(EmailAlreadyRegisteredException.class)
        .hasMessageContaining("ana@exemplo.com");

    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("recusa criacao quando o e-mail ja existe, mesmo com caixa diferente")
  void rejectsEmailAlreadyUsedRegardlessOfCase() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-456", "Outra Ana", "Ana@Exemplo.COM");
    when(userRepository.existsByCognitoId("cognito-sub-456")).thenReturn(false);
    // O service normaliza (strip + lowercase) antes de consultar - a checagem e feita com o
    // e-mail ja normalizado, entao a variacao de caixa na entrada nao deveria escapar da regra.
    when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(true);

    assertThatThrownBy(() -> userService.createFromAuthenticatedIdentity(identity))
        .isInstanceOf(EmailAlreadyRegisteredException.class);

    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("cria o perfil na primeira chamada de informacoes complementares")
  void createsProfileOnFirstAdditionalInfo() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    User existing =
        User.createFromCognitoIdentity(identity.cognitoId(), identity.name(), identity.email());
    when(userRepository.findByCognitoId("cognito-sub-123")).thenReturn(Optional.of(existing));
    when(userFinancialProfileRepository.findByUserId(existing.getId()))
        .thenReturn(Optional.empty());
    when(userFinancialProfileRepository.save(any(UserFinancialProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    UserFinancialProfile saved =
        userService.updateAdditionalInfo(
            identity,
            "+5511999998888",
            "Analista",
            LocalDate.of(2000, 1, 31),
            new BigDecimal("3500.00"));

    assertThat(saved.getUser()).isSameAs(existing);
    assertThat(saved.getPhone()).isEqualTo("+5511999998888");
    assertThat(saved.getProfession()).isEqualTo("Analista");
    assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 31));
    assertThat(saved.getMonthlyIncome()).isEqualByComparingTo("3500.00");
  }

  @Test
  @DisplayName("PATCH parcial preserva os campos que nao vieram")
  void partialUpdateKeepsOtherFields() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    User existing =
        User.createFromCognitoIdentity(identity.cognitoId(), identity.name(), identity.email());
    UserFinancialProfile profile = UserFinancialProfile.createFor(existing);
    profile.apply("+5511999998888", "Analista", LocalDate.of(2000, 1, 31), new BigDecimal("3500"));
    when(userRepository.findByCognitoId("cognito-sub-123")).thenReturn(Optional.of(existing));
    when(userFinancialProfileRepository.findByUserId(existing.getId()))
        .thenReturn(Optional.of(profile));
    when(userFinancialProfileRepository.save(any(UserFinancialProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    UserFinancialProfile saved =
        userService.updateAdditionalInfo(identity, null, "Engenheira", null, null);

    assertThat(saved.getProfession()).isEqualTo("Engenheira");
    assertThat(saved.getPhone()).isEqualTo("+5511999998888");
    assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 31));
    assertThat(saved.getMonthlyIncome()).isEqualByComparingTo("3500");
  }

  @Test
  @DisplayName("recusa atualizar informacoes complementares quando a Etapa 1 nao foi feita")
  void rejectsAdditionalInfoWhenUserDoesNotExist() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-999", "Ana Souza", "ana@exemplo.com");
    when(userRepository.findByCognitoId("cognito-sub-999")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                userService.updateAdditionalInfo(
                    identity, "+5511999998888", null, LocalDate.of(2000, 1, 31), null))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(userFinancialProfileRepository, never()).save(any());
  }

  @Test
  @DisplayName("busca por id devolve o usuario autenticado e recusa o id de outra pessoa")
  void findsByIdOnlyForTheAuthenticatedUser() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    User existing =
        User.createFromCognitoIdentity(identity.cognitoId(), identity.name(), identity.email());
    when(userRepository.findByCognitoId("cognito-sub-123")).thenReturn(Optional.of(existing));

    assertThat(userService.findByIdForAuthenticatedIdentity(identity, existing.getId()))
        .isSameAs(existing);
    assertThatThrownBy(
            () -> userService.findByIdForAuthenticatedIdentity(identity, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("busca usuario pela identidade autenticada")
  void findsByAuthenticatedIdentity() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    User existing =
        User.createFromCognitoIdentity(identity.cognitoId(), identity.name(), identity.email());
    when(userRepository.findByCognitoId("cognito-sub-123")).thenReturn(Optional.of(existing));

    User found = userService.findByAuthenticatedIdentity(identity);

    assertThat(found).isEqualTo(existing);
  }

  @Test
  @DisplayName("busca por identidade inexistente lanca ResourceNotFoundException")
  void throwsWhenIdentityHasNoLocalUser() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-999", "Ana Souza", "ana@exemplo.com");
    when(userRepository.findByCognitoId("cognito-sub-999")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.findByAuthenticatedIdentity(identity))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("e-mail que nenhum usuario usa esta disponivel")
  void emailIsAvailableWhenNoUserUsesIt() {
    when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(false);

    assertThat(userService.isEmailAvailable("ana@exemplo.com")).isTrue();
  }

  @Test
  @DisplayName("e-mail de um usuario existente nao esta disponivel")
  void emailIsNotAvailableWhenAUserUsesIt() {
    when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(true);

    assertThat(userService.isEmailAvailable("ana@exemplo.com")).isFalse();
  }

  @Test
  @DisplayName("consulta de e-mail normaliza como o cadastro: sem espacos e em minusculas")
  void emailAvailabilityNormalizesLikeRegistration() {
    when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(true);

    assertThat(userService.isEmailAvailable("  Ana@Exemplo.COM ")).isFalse();
  }
}
