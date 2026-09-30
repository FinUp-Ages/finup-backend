package br.com.finup.repository;

import br.com.finup.model.Transaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso a transacoes e verificacao das referencias exigidas para cadastra-las. */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

  /**
   * Tipo da categoria, quando ela esta disponivel para o usuario: ou e dele, ou e padrao do sistema
   * — mesma regra que o CRUD de categorias aplica na listagem. Sem o filtro por dono, daria para
   * anexar a propria transacao a categoria privada de outro usuario.
   *
   * <p>Devolve o tipo, e nao um booleano, porque quem cadastra precisa das duas respostas na mesma
   * consulta: se a categoria esta disponivel e se o tipo dela combina com o da transacao. Manter as
   * duas aqui evita reimplementar a regra de disponibilidade fora deste repositorio.
   *
   * <p>O retorno e {@code String} porque a consulta e nativa e a coluna guarda o nome da constante;
   * a conversao para {@link br.com.finup.model.TransactionType} fica com quem chama.
   */
  @Query(
      value =
          "SELECT type FROM categories WHERE id = :id"
              + " AND (user_id = :userId OR is_default = true)",
      nativeQuery = true)
  Optional<String> findAvailableCategoryTypeForUser(
      @Param("id") UUID id, @Param("userId") UUID userId);

  /**
   * Meio de pagamento tem que ser do proprio usuario: a coluna {@code user_id} e {@code NOT NULL},
   * entao todo cartao ou conta tem dono e nao existe equivalente de "padrao do sistema" aqui.
   */
  @Query(
      value = "SELECT EXISTS (SELECT 1 FROM payment_methods WHERE id = :id AND user_id = :userId)",
      nativeQuery = true)
  boolean existsPaymentMethodForUser(@Param("id") UUID id, @Param("userId") UUID userId);
}
