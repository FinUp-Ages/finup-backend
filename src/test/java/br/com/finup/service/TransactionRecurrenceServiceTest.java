package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.exception.InvalidRecurrencePeriodException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.RecurrenceFrequency;
import br.com.finup.model.TransactionRecurrence;
import br.com.finup.model.TransactionType;
import br.com.finup.model.User;
import br.com.finup.repository.TransactionRecurrenceRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Garante as validacoes de referencias e o cadastro de recorrencias de transacao. */
@ExtendWith(MockitoExtension.class)
class TransactionRecurrenceServiceTest {

  @Mock private TransactionRecurrenceRepository transactionRecurrenceRepository;
  @Mock private UserService userService;

  @InjectMocks private TransactionRecurrenceService transactionRecurrenceService;

  private User mockUser() {
    return User.createFromCognitoIdentity("cognito-sub-ana", "Ana Souza", "ana@exemplo.com");
  }

  private AuthenticatedIdentity identity(User user) {
    return new AuthenticatedIdentity(user.getCognitoId(), user.getName(), user.getEmail());
  }

  @Test
  @DisplayName("cadastra a recorrencia quando usuario e categoria existem")
  void registersWhenReferencesExist() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    UUID categoryId = UUID.randomUUID();
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(transactionRecurrenceRepository.existsCategoryById(categoryId)).thenReturn(true);
    when(transactionRecurrenceRepository.save(any(TransactionRecurrence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    TransactionRecurrence recurrence =
        register(identity, categoryId, null, LocalDate.of(2026, 9, 1), null);

    assertThat(recurrence.getId()).isNotNull();
    assertThat(recurrence.getUserId()).isEqualTo(user.getId());
    assertThat(recurrence.getCategoryId()).isEqualTo(categoryId);
    verify(transactionRecurrenceRepository, never()).existsPaymentMethodById(any());
  }

  @Test
  @DisplayName("associa o meio de pagamento quando ele e informado e existe")
  void registersWithExistingPaymentMethod() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    UUID categoryId = UUID.randomUUID();
    UUID paymentMethodId = UUID.randomUUID();
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(transactionRecurrenceRepository.existsCategoryById(categoryId)).thenReturn(true);
    when(transactionRecurrenceRepository.existsPaymentMethodById(paymentMethodId)).thenReturn(true);
    when(transactionRecurrenceRepository.save(any(TransactionRecurrence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    TransactionRecurrence recurrence =
        register(identity, categoryId, paymentMethodId, LocalDate.of(2026, 9, 1), null);

    assertThat(recurrence.getPaymentMethodId()).isEqualTo(paymentMethodId);
  }

  @Test
  @DisplayName("propaga o 404 quando a identidade autenticada ainda nao tem usuario local")
  void rejectsIdentityWithoutLocalUser() {
    AuthenticatedIdentity identity = new AuthenticatedIdentity("sub-sem-usuario", null, "x@y.com");
    when(userService.findByAuthenticatedIdentity(identity))
        .thenThrow(new ResourceNotFoundException("Usuario nao encontrado"));

    assertThatThrownBy(() -> register(identity, UUID.randomUUID(), null, LocalDate.now(), null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Usuario");
    verify(transactionRecurrenceRepository, never()).save(any());
  }

  @Test
  @DisplayName("recusa quando a categoria nao existe")
  void rejectsUnknownCategory() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    UUID categoryId = UUID.randomUUID();
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(transactionRecurrenceRepository.existsCategoryById(categoryId)).thenReturn(false);

    assertThatThrownBy(() -> register(identity, categoryId, null, LocalDate.now(), null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Categoria");
    verify(transactionRecurrenceRepository, never()).save(any());
  }

  @Test
  @DisplayName("recusa quando o meio de pagamento informado nao existe")
  void rejectsUnknownPaymentMethod() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    UUID categoryId = UUID.randomUUID();
    UUID paymentMethodId = UUID.randomUUID();
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(transactionRecurrenceRepository.existsCategoryById(categoryId)).thenReturn(true);
    when(transactionRecurrenceRepository.existsPaymentMethodById(paymentMethodId))
        .thenReturn(false);

    assertThatThrownBy(() -> register(identity, categoryId, paymentMethodId, LocalDate.now(), null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Meio de pagamento");
    verify(transactionRecurrenceRepository, never()).save(any());
  }

  @Test
  @DisplayName("recusa quando a data de termino e anterior a data de inicio")
  void rejectsEndDateBeforeStartDate() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    UUID categoryId = UUID.randomUUID();
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(transactionRecurrenceRepository.existsCategoryById(categoryId)).thenReturn(true);

    assertThatThrownBy(
            () ->
                register(
                    identity, categoryId, null, LocalDate.of(2026, 9, 5), LocalDate.of(2026, 1, 1)))
        .isInstanceOf(InvalidRecurrencePeriodException.class);
    verify(transactionRecurrenceRepository, never()).save(any());
  }

  @Test
  @DisplayName("findDueOn devolve apenas as recorrencias do usuario que caem na data informada")
  void findDueOnFiltersByDate() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    TransactionRecurrence dueToday =
        TransactionRecurrence.register(
            user.getId(),
            UUID.randomUUID(),
            null,
            TransactionType.EXPENSE,
            "Conta de luz",
            new BigDecimal("250.00"),
            RecurrenceFrequency.MONTHLY,
            5,
            LocalDate.of(2026, 1, 1),
            null);
    TransactionRecurrence dueLater =
        TransactionRecurrence.register(
            user.getId(),
            UUID.randomUUID(),
            null,
            TransactionType.EXPENSE,
            "Internet",
            new BigDecimal("120.00"),
            RecurrenceFrequency.MONTHLY,
            20,
            LocalDate.of(2026, 1, 1),
            null);
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(transactionRecurrenceRepository.findByUserId(user.getId()))
        .thenReturn(List.of(dueToday, dueLater));

    List<TransactionRecurrence> due =
        transactionRecurrenceService.findDueOn(identity, LocalDate.of(2026, 9, 5));

    assertThat(due).containsExactly(dueToday);
  }

  private TransactionRecurrence register(
      AuthenticatedIdentity identity,
      UUID categoryId,
      UUID paymentMethodId,
      LocalDate startDate,
      LocalDate endDate) {
    return transactionRecurrenceService.register(
        identity,
        categoryId,
        paymentMethodId,
        TransactionType.EXPENSE,
        "Conta de luz",
        new BigDecimal("250.00"),
        RecurrenceFrequency.MONTHLY,
        5,
        startDate,
        endDate);
  }
}
