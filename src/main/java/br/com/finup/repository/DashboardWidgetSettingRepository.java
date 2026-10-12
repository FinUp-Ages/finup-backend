package br.com.finup.repository;

import br.com.finup.model.DashboardWidgetSetting;
import br.com.finup.model.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DashboardWidgetSettingRepository
    extends JpaRepository<DashboardWidgetSetting, UUID> {

  List<DashboardWidgetSetting> findByUserOrderByPositionAscWidgetIdAsc(User user);

  /**
   * Apaga a configuração do usuário na hora, antes dos inserts seguintes. Com {@code deleteBy...}
   * derivado o Hibernate reordenaria os INSERTs para antes dos DELETEs e violaria a unicidade de
   * (usuário, widget) ao regravar o mesmo widget.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from DashboardWidgetSetting w where w.user = :user")
  void deleteAllByUser(@Param("user") User user);

  /**
   * Trava a linha do usuário até o fim da transação ({@code SELECT ... FOR UPDATE}). Serializa
   * {@code PUT}s simultâneos do mesmo admin: o segundo espera o primeiro terminar e então substitui
   * a lista, em vez de bater na unicidade de (usuário, widget) e responder 500. Exige transação
   * ativa.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  Optional<User> lockUserById(@Param("id") UUID id);
}
