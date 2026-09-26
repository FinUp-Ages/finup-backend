package br.com.finup.repository;

import br.com.finup.model.FinUpScoreInputs;
import br.com.finup.model.User;

/**
 * Acesso aos dados agregados que o FinUp Score consome ({@code UserFinancialProfiles}, {@code
 * Transactions}, {@code Debts}, {@code Goals}, {@code Investments}).
 *
 * <p>Mesmo papel que {@link UserRepository} tem para {@code Users}: {@code FinUpScoreService}
 * depende so desta interface, nunca da implementacao.
 */
public interface FinUpScoreDataProvider {

  FinUpScoreInputs loadInputs(User user);
}
