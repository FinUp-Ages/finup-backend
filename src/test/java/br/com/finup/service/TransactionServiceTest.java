package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.finup.exception.IncompatibleTransactionCategoryException;
import br.com.finup.exception.InvalidTransactionRecurrenceException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.RecurrenceFrequency;
import br.com.finup.model.Transaction;
import br.com.finup.model.TransactionType;
import br.com.finup.model.User;
import br.com.finup.repository.TransactionRepository;
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

/** Garante as validacoes de referencias e o cadastro executado pelo service de transacoes. */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private UserService userService;

  @InjectMocks private TransactionService transactionService;

  private User mockUser() {
    return User.createFromCognitoIdentity("cognito-sub-ana", "Ana Souza", "ana@exemplo.com");
  }

  private AuthenticatedIdentity identity(User user) {
    return new AuthenticatedIdentity(user.getCognitoId(), user.getName(), user.getEmail());
  }

  @Test
  @DisplayName("cadastra transacao recorrente sem consultar meio de pagamento nao informado")
  void registersRecurringWithoutPaymentMethod() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    UUID categoryId = UUID.randomUUID();
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(transactionRepository.findAvailableCategoryTypeForUser(categoryId, user.getId()))
        .thenReturn(Optional.of("INCOME"));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Transaction transaction =
        transactionService.register(
            identity,
            categoryId,
            null,
            TransactionType.INCOME,
            "Salario",
            new BigDecimal("5000.00"),
            LocalDate.of(2026, 9, 12),
            true,
            RecurrenceFrequency.MONTHLY);

    assertThat(transaction.getId()).isNotNull();
    assertThat(transaction.getUserId()).isEqualTo(user.getId());
    assertThat(transaction.getCategoryId()).isEqualTo(categoryId);
    assertThat(transaction.getPaymentMethodId()).isNull();
    assertThat(transaction.getType()).isEqualTo(TransactionType.INCOME);
    assertThat(transaction.isRecurring()).isTrue();
    assertThat(transaction.getRecurrenceFrequency()).isEqualTo(RecurrenceFrequency.MONTHLY);
    assertThat(transaction.getLastOccurrenceDate()).isEqualTo(LocalDate.of(2026, 9, 12));
    verify(transactionRepository, never()).existsPaymentMethodForUser(any(), any());
  }

  @Test
  @DisplayName("associa o meio de pagamento quando ele e informado e existe")
  void registersWithExistingPaymentMethod() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    UUID categoryId = UUID.randomUUID();
    UUID paymentMethodId = UUID.randomUUID();
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(transactionRepository.findAvailableCategoryTypeForUser(categoryId, user.getId()))
        .thenReturn(Optional.of("EXPENSE"));
    when(transactionRepository.existsPaymentMethodForUser(paymentMethodId, user.getId()))
        .thenReturn(true);
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Transaction transaction =
        transactionService.register(
            identity,
            categoryId,
            paymentMethodId,
            TransactionType.EXPENSE,
            "Supermercado",
            new BigDecimal("320.00"),
            LocalDate.of(2026, 9, 12),
            false,
            null);

    assertThat(transaction.getPaymentMethodId()).isEqualTo(paymentMethodId);
  }

  @Test
  @DisplayName("propaga o 404 quando a identidade autenticada ainda nao tem usuario local")
  void rejectsIdentityWithoutLocalUser() {
    AuthenticatedIdentity identity = new AuthenticatedIdentity("sub-sem-usuario", null, "x@y.com");
    when(userService.findByAuthenticatedIdentity(identity))
        .thenThrow(new ResourceNotFoundException("Usuario nao encontrado"));

    assertThatThrownBy(() -> register(identity, UUID.randomUUID(), null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Usuario");
    verify(transactionRepository, never()).save(any());
  }

  @Test
  @DisplayName("recusa transacao quando a categoria nao existe ou e de outro usuario")
  void rejectsUnknownCategory() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    UUID categoryId = UUID.randomUUID();
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(transactionRepository.findAvailableCategoryTypeForUser(categoryId, user.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> register(identity, categoryId, null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Categoria");
    verify(transactionRepository, never()).save(any());
  }

  @Test
  @DisplayName("recusa transacao quando o meio de pagamento nao existe ou e de outro usuario")
  void rejectsUnknownPaymentMethod() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    UUID categoryId = UUID.randomUUID();
    UUID paymentMethodId = UUID.randomUUID();
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(transactionRepository.findAvailableCategoryTypeForUser(categoryId, user.getId()))
        .thenReturn(Optional.of("EXPENSE"));
    when(transactionRepository.existsPaymentMethodForUser(paymentMethodId, user.getId()))
        .thenReturn(false);

    assertThatThrownBy(() -> register(identity, categoryId, paymentMethodId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Meio de pagamento");
    verify(transactionRepository, never()).save(any());
  }

  @Test
  @DisplayName("recusa transacao quando o tipo da categoria difere do tipo da transacao")
  void rejectsCategoryWithIncompatibleType() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    UUID categoryId = UUID.randomUUID();
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(transactionRepository.findAvailableCategoryTypeForUser(categoryId, user.getId()))
        .thenReturn(Optional.of("INCOME"));

    assertThatThrownBy(() -> register(identity, categoryId, null))
        .isInstanceOf(IncompatibleTransactionCategoryException.class)
        .hasMessageContaining("INCOME")
        .hasMessageContaining("EXPENSE");
    verify(transactionRepository, never()).save(any());
  }

  @Test
  @DisplayName("nao consulta o meio de pagamento quando a categoria e incompativel")
  void skipsPaymentMethodWhenCategoryIsIncompatible() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    UUID categoryId = UUID.randomUUID();
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(transactionRepository.findAvailableCategoryTypeForUser(categoryId, user.getId()))
        .thenReturn(Optional.of("INCOME"));

    assertThatThrownBy(() -> register(identity, categoryId, UUID.randomUUID()))
        .isInstanceOf(IncompatibleTransactionCategoryException.class);
    verify(transactionRepository, never()).existsPaymentMethodForUser(any(), any());
  }

  /**
   * Categoria indisponivel e tipo incompativel ao mesmo tempo tem que responder 404, e nao 422: um
   * 422 aqui confirmaria a existencia do id da categoria privada de outro usuario.
   */
  @Test
  @DisplayName("categoria indisponivel responde 404 mesmo quando o tipo tambem seria incompativel")
  void prefersNotFoundOverIncompatibleTypeForUnavailableCategory() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    UUID otherUsersIncomeCategoryId = UUID.randomUUID();
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(transactionRepository.findAvailableCategoryTypeForUser(
            otherUsersIncomeCategoryId, user.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> register(identity, otherUsersIncomeCategoryId, null))
        .isInstanceOf(ResourceNotFoundException.class)
        .isNotInstanceOf(IncompatibleTransactionCategoryException.class)
        .hasMessageContaining("Categoria");
    verify(transactionRepository, never()).save(any());
  }

  private Transaction register(
      AuthenticatedIdentity identity, UUID categoryId, UUID paymentMethodId) {
    return transactionService.register(
        identity,
        categoryId,
        paymentMethodId,
        TransactionType.EXPENSE,
        "Compra",
        new BigDecimal("10.00"),
        LocalDate.of(2026, 9, 12),
        false,
        null);
  }

  @Test
  @DisplayName("recusa transacao recorrente sem periodicidade")
  void rejectsRecurringTransactionWithoutFrequency() {
    assertThatThrownBy(() -> registerWithRecurrence(true, null))
        .isInstanceOf(InvalidTransactionRecurrenceException.class)
        .hasMessageContaining("precisa informar recurrenceFrequency");
    verifyNoInteractions(userService, transactionRepository);
  }

  @Test
  @DisplayName("recusa transacao nao recorrente com periodicidade")
  void rejectsNonRecurringTransactionWithFrequency() {
    assertThatThrownBy(() -> registerWithRecurrence(false, RecurrenceFrequency.MONTHLY))
        .isInstanceOf(InvalidTransactionRecurrenceException.class)
        .hasMessageContaining("nao pode informar recurrenceFrequency");
    verifyNoInteractions(userService, transactionRepository);
  }

  private Transaction registerWithRecurrence(
      boolean recurring, RecurrenceFrequency recurrenceFrequency) {
    User user = mockUser();
    return transactionService.register(
        identity(user),
        UUID.randomUUID(),
        null,
        TransactionType.EXPENSE,
        "Conta de luz",
        new BigDecimal("250.00"),
        LocalDate.of(2026, 9, 5),
        recurring,
        recurrenceFrequency);
  }
}
