package br.com.finup.repository;

import br.com.finup.model.PaymentMethod;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

  /**
   * Formas de pagamento ativas que o usuario pode usar: as padrao do sistema, na ordem do seed, e
   * depois as proprias.
   */
  @Query(
      "SELECT pm FROM PaymentMethod pm"
          + " WHERE pm.isActive = true AND (pm.isDefault = true OR pm.userId = :userId)"
          + " ORDER BY pm.isDefault DESC, pm.createdAt ASC, pm.name ASC")
  List<PaymentMethod> findActiveAvailableForUser(@Param("userId") UUID userId);
}
