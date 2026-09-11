package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.exception.EmailAlreadyRegisteredException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.exception.UserAlreadyRegisteredException;
import br.com.finup.model.User;
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

  @InjectMocks private UserService userService;

  @Test
  @DisplayName("cria usuario quando a identidade do Cognito ainda nao tem registro local")
  void createsWhenIdentityIsNew() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    when(userRepository.existsByCognitoId("cognito-sub-123")).thenReturn(false);
    when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(false);
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User user = userService.createFromAuthenticatedIdentity(identity);

    assertThat(user.getId()).isNotNull();
    assertThat(user.getCognitoId()).isEqualTo("cognito-sub-123");
    assertThat(user.getName()).isEqualTo("Ana Souza");
    assertThat(user.getEmail()).isEqualTo("ana@exemplo.com");
    assertThat(user.getCreatedAt()).isNotNull();
    assertThat(user.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("recusa criacao quando a identidade ja tem usuario local e nao chega a gravar")
  void rejectsDuplicateCognitoIdentity() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    when(userRepository.existsByCognitoId("cognito-sub-123")).thenReturn(true);

    assertThatThrownBy(() -> userService.createFromAuthenticatedIdentity(identity))
        .isInstanceOf(UserAlreadyRegisteredException.class)
        .hasMessageContaining("cognito-sub-123");

    verify(userRepository, never()).save(any());
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

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("buscar por id inexistente lanca ResourceNotFoundException")
  void throwsWhenIdDoesNotExist() {
    UUID id = UUID.randomUUID();
    when(userRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.findById(id))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("atualiza informacoes complementares de um usuario ja criado")
  void updatesAdditionalInfoForExistingUser() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    User existing =
        User.createFromCognitoIdentity(identity.cognitoId(), identity.name(), identity.email());
    when(userRepository.findByCognitoId("cognito-sub-123")).thenReturn(Optional.of(existing));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User updated =
        userService.updateAdditionalInfo(
            identity, LocalDate.of(1998, 4, 12), new BigDecimal("3500.00"), "MODERADO");

    assertThat(updated.getBirthDate()).isEqualTo(LocalDate.of(1998, 4, 12));
    assertThat(updated.getMonthlyIncome()).isEqualByComparingTo("3500.00");
    assertThat(updated.getFinancialProfile()).isEqualTo("MODERADO");
    assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(existing.getCreatedAt());
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
                    identity, LocalDate.of(1998, 4, 12), new BigDecimal("3500.00"), "MODERADO"))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(userRepository, never()).save(any());
  }
}
