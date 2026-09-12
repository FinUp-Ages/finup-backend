package br.com.finup.repository;

import br.com.finup.model.Transaction;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso a transacoes e verificacao das referencias exigidas para cadastra-las. */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

  @Query(value = "SELECT EXISTS (SELECT 1 FROM users WHERE id = :id)", nativeQuery = true)
  boolean existsUserById(@Param("id") UUID id);

  @Query(value = "SELECT EXISTS (SELECT 1 FROM categories WHERE id = :id)", nativeQuery = true)
  boolean existsCategoryById(@Param("id") UUID id);

  @Query(value = "SELECT EXISTS (SELECT 1 FROM payment_methods WHERE id = :id)", nativeQuery = true)
  boolean existsPaymentMethodById(@Param("id") UUID id);
}
