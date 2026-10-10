package br.com.finup.service;

import br.com.finup.dto.DashboardWidgetItem;
import br.com.finup.dto.DashboardWidgetsRequest;
import br.com.finup.dto.DashboardWidgetsResponse;
import br.com.finup.exception.BusinessException;
import br.com.finup.mapper.DashboardWidgetMapper;
import br.com.finup.model.DashboardWidgetSetting;
import br.com.finup.model.User;
import br.com.finup.repository.DashboardWidgetSettingRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Configuração dos widgets do dashboard, guardada por usuário (admin) autenticado. */
@Service
public class DashboardWidgetService {

  private final DashboardWidgetSettingRepository repository;
  private final UserService userService;

  public DashboardWidgetService(
      DashboardWidgetSettingRepository repository, UserService userService) {
    this.repository = repository;
    this.userService = userService;
  }

  /** Sem configuração salva, devolve lista vazia. */
  @Transactional(readOnly = true)
  public DashboardWidgetsResponse find(AuthenticatedIdentity identity) {
    User user = userService.findByAuthenticatedIdentity(identity);
    return DashboardWidgetMapper.toResponse(
        repository.findByUserOrderByPositionAscWidgetIdAsc(user));
  }

  /** Troca a configuração inteira do usuário pela enviada. Lista vazia limpa a configuração. */
  @Transactional
  public DashboardWidgetsResponse replace(
      AuthenticatedIdentity identity, DashboardWidgetsRequest request) {
    requireUniqueIds(request.widgets());
    User user = userService.findByAuthenticatedIdentity(identity);

    repository.deleteAllByUser(user);
    List<DashboardWidgetSetting> saved =
        repository.saveAll(
            request.widgets().stream()
                .map(w -> DashboardWidgetSetting.create(user, w.id(), w.enabled(), w.position()))
                .toList());

    return DashboardWidgetMapper.toResponse(
        saved.stream()
            .sorted(
                java.util.Comparator.comparingInt(DashboardWidgetSetting::getPosition)
                    .thenComparing(DashboardWidgetSetting::getWidgetId))
            .toList());
  }

  private static void requireUniqueIds(List<DashboardWidgetItem> widgets) {
    Set<String> seen = new HashSet<>();
    for (DashboardWidgetItem widget : widgets) {
      if (!seen.add(widget.id().strip())) {
        throw new BusinessException("Widget repetido: " + widget.id().strip());
      }
    }
  }
}
