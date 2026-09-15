package br.com.finup.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finup.model.RecurrenceFrequency;
import br.com.finup.model.RecurrenceType;
import br.com.finup.model.TransactionRecurrence;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Valida o mapeamento JPA de recorrencias e as consultas das referencias no banco. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class TransactionRecurrenceRepositoryTest {

  @Autowired private TransactionRecurrenceRepository transactionRecurrenceRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void createReferenceTables() {
    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS users (id UUID PRIMARY KEY)");
    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS categories (id UUID PRIMARY KEY)");
    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS payment_methods (id UUID PRIMARY KEY)");
  }

  @Test
  @DisplayName("encontra usuario, categoria e meio de pagamento existentes")
  void findsExistingReferences() {
    UUID userId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID paymentMethodId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, cognito_id, email, created_at, updated_at) VALUES (?, ?, ?, now(), now())",
        userId,
        "cognito-" + userId,
        userId + "@example.com");
    jdbcTemplate.update(
        "INSERT INTO categories (id, name, type, is_default, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, now(), now())",
        categoryId,
        "Alimentação",
        "EXPENSE",
        false);
    jdbcTemplate.update("INSERT INTO payment_methods (id) VALUES (?)", paymentMethodId);

    assertThat(transactionRecurrenceRepository.existsUserById(userId)).isTrue();
    assertThat(transactionRecurrenceRepository.existsCategoryById(categoryId)).isTrue();
    assertThat(transactionRecurrenceRepository.existsPaymentMethodById(paymentMethodId)).isTrue();
    assertThat(transactionRecurrenceRepository.existsCategoryById(UUID.randomUUID())).isFalse();
  }

  @Test
  @DisplayName("persiste todos os campos da recorrencia com meio de pagamento opcional")
  void persistsRecurrenceWithoutPaymentMethod() {
    TransactionRecurrence recurrence =
        TransactionRecurrence.register(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            RecurrenceType.EXPENSE,
            "Conta de luz",
            new BigDecimal("250.00"),
            RecurrenceFrequency.MONTHLY,
            5,
            LocalDate.of(2026, 1, 1),
            null);

    TransactionRecurrence saved = transactionRecurrenceRepository.saveAndFlush(recurrence);

    assertThat(transactionRecurrenceRepository.findById(saved.getId()))
        .get()
        .satisfies(
            found -> {
              assertThat(found.getPaymentMethodId()).isNull();
              assertThat(found.getAmount()).isEqualByComparingTo("250.00");
              assertThat(found.getFrequency()).isEqualTo(RecurrenceFrequency.MONTHLY);
              assertThat(found.getDayOfMonth()).isEqualTo(5);
              assertThat(found.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
              assertThat(found.getEndDate()).isNull();
              assertThat(found.getCreatedAt()).isNotNull();
              assertThat(found.getUpdatedAt()).isNotNull();
            });
  }

  @Test
  @DisplayName("findByUserId devolve apenas as recorrencias do usuario informado")
  void findByUserIdFiltersByUser() {
    UUID userId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    transactionRecurrenceRepository.saveAndFlush(
        TransactionRecurrence.register(
            userId,
            UUID.randomUUID(),
            null,
            RecurrenceType.EXPENSE,
            "Conta de luz",
            new BigDecimal("250.00"),
            RecurrenceFrequency.MONTHLY,
            5,
            LocalDate.of(2026, 1, 1),
            null));
    transactionRecurrenceRepository.saveAndFlush(
        TransactionRecurrence.register(
            otherUserId,
            UUID.randomUUID(),
            null,
            RecurrenceType.INCOME,
            "Salario",
            new BigDecimal("5000.00"),
            RecurrenceFrequency.MONTHLY,
            1,
            LocalDate.of(2026, 1, 1),
            null));

    assertThat(transactionRecurrenceRepository.findByUserId(userId))
        .extracting(TransactionRecurrence::getUserId)
        .containsExactly(userId);
  }
}
