package br.com.finup.mapper;

import br.com.finup.dto.DashboardWidgetItem;
import br.com.finup.dto.DashboardWidgetsResponse;
import br.com.finup.model.DashboardWidgetSetting;
import java.util.List;

/** Converte a configuração de widgets para o DTO de saída. */
public final class DashboardWidgetMapper {

  private DashboardWidgetMapper() {}

  public static DashboardWidgetsResponse toResponse(List<DashboardWidgetSetting> settings) {
    return new DashboardWidgetsResponse(
        settings.stream()
            .map(s -> new DashboardWidgetItem(s.getWidgetId(), s.isEnabled(), s.getPosition()))
            .toList());
  }
}
