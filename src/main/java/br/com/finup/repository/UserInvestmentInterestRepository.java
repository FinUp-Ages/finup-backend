package br.com.finup.repository;

import br.com.finup.model.UserInvestmentInterest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso aos interesses de investimento do perfil financeiro. */
public interface UserInvestmentInterestRepository
    extends JpaRepository<UserInvestmentInterest, UUID> {

  void deleteByUserFinancialProfileId(UUID userFinancialProfileId);
}
