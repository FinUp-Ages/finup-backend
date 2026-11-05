package br.com.finup.repository;

import br.com.finup.model.TriageResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso às respostas da triagem. Toda consulta é filtrada pelo usuário. */
public interface TriageResponseRepository extends JpaRepository<TriageResponse, UUID> {

  Optional<TriageResponse> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
}
