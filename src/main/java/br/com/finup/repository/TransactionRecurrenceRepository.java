package br.com.finup.repository;

import br.com.finup.model.TransactionRecurrence;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso a recorrencias de transacao e verificacao das referencias exigidas para cadastra-las. */
public interface TransactionRecurrenceRepository
    extends JpaRepository<TransactionRecurrence, UUID> {

  List<TransactionRecurrence> findByUserId(UUID userId);

  /**
   * Categoria que o usuario pode usar: ou e dele, ou e padrao do sistema — mesma regra que o CRUD
   * de categorias aplica na listagem. Sem o filtro por dono, daria para anexar a propria transacao
   * a categoria privada de outro usuario.
   */
  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM categories WHERE id = :id"
              + " AND (user_id = :userId OR is_default = true))",
      nativeQuery = true)
  boolean existsCategoryAvailableForUser(@Param("id") UUID id, @Param("userId") UUID userId);

  /**
   * Meio de pagamento tem que ser do proprio usuario: a coluna {@code user_id} e {@code NOT NULL},
   * entao todo cartao ou conta tem dono e nao existe equivalente de "padrao do sistema" aqui.
   */
  @Query(
      value = "SELECT EXISTS (SELECT 1 FROM payment_methods WHERE id = :id AND user_id = :userId)",
      nativeQuery = true)
  boolean existsPaymentMethodForUser(@Param("id") UUID id, @Param("userId") UUID userId);
}
