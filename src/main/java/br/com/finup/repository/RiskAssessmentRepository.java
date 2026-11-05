package br.com.finup.repository;

import br.com.finup.model.RiskAssessment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso às avaliações de risco. Toda consulta é filtrada pelo usuário. */
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {

  Optional<RiskAssessment> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
}
