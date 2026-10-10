package br.com.finup.repository;

import br.com.finup.model.DashboardWidgetSetting;
import br.com.finup.model.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
