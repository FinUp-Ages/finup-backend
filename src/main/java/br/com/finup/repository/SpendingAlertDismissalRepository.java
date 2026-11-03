package br.com.finup.repository;

import br.com.finup.model.SpendingAlertDismissal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso às dispensas de alerta de gastos. Toda consulta é filtrada pelo usuário. */
public interface SpendingAlertDismissalRepository
    extends JpaRepository<SpendingAlertDismissal, UUID> {

  List<SpendingAlertDismissal> findByUserId(UUID userId);

  Optional<SpendingAlertDismissal> findByUserIdAndCategoryId(UUID userId, UUID categoryId);
}
