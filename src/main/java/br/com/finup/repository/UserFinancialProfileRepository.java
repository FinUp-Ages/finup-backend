package br.com.finup.repository;

import br.com.finup.model.UserFinancialProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso a dados das informacoes complementares do usuario. */
public interface UserFinancialProfileRepository extends JpaRepository<UserFinancialProfile, UUID> {

  Optional<UserFinancialProfile> findByUserId(UUID userId);
}
