package br.com.finup.repository;

import br.com.finup.model.PaymentMethod;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

  List<PaymentMethod> findByUserIdAndIsActiveTrue(UUID userId);
}
