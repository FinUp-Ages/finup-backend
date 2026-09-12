package br.com.finup.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finup.model.Transaction;
import br.com.finup.model.TransactionType;
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

/** Valida o mapeamento JPA de transacoes e as consultas das referencias no banco. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class TransactionRepositoryTest {

  @Autowired private TransactionRepository transactionRepository;

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
    jdbcTemplate.update("INSERT INTO users (id) VALUES (?)", userId);
    jdbcTemplate.update("INSERT INTO categories (id) VALUES (?)", categoryId);
    jdbcTemplate.update("INSERT INTO payment_methods (id) VALUES (?)", paymentMethodId);

    assertThat(transactionRepository.existsUserById(userId)).isTrue();
    assertThat(transactionRepository.existsCategoryById(categoryId)).isTrue();
    assertThat(transactionRepository.existsPaymentMethodById(paymentMethodId)).isTrue();
    assertThat(transactionRepository.existsCategoryById(UUID.randomUUID())).isFalse();
  }

  @Test
  @DisplayName("persiste todos os campos da transacao com meio de pagamento opcional")
  void persistsTransactionWithoutPaymentMethod() {
    Transaction transaction =
        Transaction.register(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            TransactionType.EXPENSE,
            "Supermercado",
            new BigDecimal("320.00"),
            LocalDate.of(2026, 9, 12),
            false);

    Transaction saved = transactionRepository.saveAndFlush(transaction);

    assertThat(transactionRepository.findById(saved.getId()))
        .get()
        .satisfies(
            found -> {
              assertThat(found.getPaymentMethodId()).isNull();
              assertThat(found.getAmount()).isEqualByComparingTo("320.00");
              assertThat(found.getTransactionDate()).isEqualTo(LocalDate.of(2026, 9, 12));
              assertThat(found.isRecurring()).isFalse();
              assertThat(found.getCreatedAt()).isNotNull();
              assertThat(found.getUpdatedAt()).isNotNull();
            });
  }
}
