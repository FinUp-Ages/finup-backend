package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.dto.DashboardWidgetItem;
import br.com.finup.dto.DashboardWidgetsRequest;
import br.com.finup.dto.DashboardWidgetsResponse;
import br.com.finup.exception.BusinessException;
import br.com.finup.model.DashboardWidgetSetting;
import br.com.finup.model.User;
import br.com.finup.repository.DashboardWidgetSettingRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardWidgetServiceTest {

  @Mock private DashboardWidgetSettingRepository repository;
  @Mock private UserService userService;

  @InjectMocks private DashboardWidgetService service;

  private final User user =
      User.createFromCognitoIdentity("cognito-sub-ana", "Ana Souza", "ana@exemplo.com");
  private final AuthenticatedIdentity identity =
      new AuthenticatedIdentity(user.getCognitoId(), user.getName(), user.getEmail());

  @Test
  @DisplayName("GET sem configuracao salva devolve lista vazia")
  void findWithoutSettingsReturnsEmptyList() {
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(repository.findByUserOrderByPositionAscWidgetIdAsc(user)).thenReturn(List.of());

    assertThat(service.find(identity).widgets()).isEmpty();
  }

  @Test
  @DisplayName("GET devolve os widgets gravados")
  void findReturnsStoredWidgets() {
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(repository.findByUserOrderByPositionAscWidgetIdAsc(user))
        .thenReturn(List.of(DashboardWidgetSetting.create(user, "NEW_CLIENTS", true, 0)));

    DashboardWidgetsResponse response = service.find(identity);

    assertThat(response.widgets()).containsExactly(new DashboardWidgetItem("NEW_CLIENTS", true, 0));
  }

  @Test
  @DisplayName("PUT apaga a configuracao anterior antes de gravar a nova, ordenada por position")
  void replaceDeletesThenSavesSortedByPosition() {
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(repository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
    DashboardWidgetsRequest request =
        new DashboardWidgetsRequest(
            List.of(
                new DashboardWidgetItem("REVENUE", false, 1),
                new DashboardWidgetItem("NEW_CLIENTS", true, 0)));

    DashboardWidgetsResponse response = service.replace(identity, request);

    InOrder order = inOrder(repository);
    order.verify(repository).deleteAllByUser(user);
    order.verify(repository).saveAll(anyList());
    assertThat(response.widgets())
        .containsExactly(
            new DashboardWidgetItem("NEW_CLIENTS", true, 0),
            new DashboardWidgetItem("REVENUE", false, 1));
  }

  @Test
  @DisplayName("PUT com lista vazia limpa a configuracao")
  void replaceWithEmptyListClearsSettings() {
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(repository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

    DashboardWidgetsResponse response =
        service.replace(identity, new DashboardWidgetsRequest(List.of()));

    verify(repository).deleteAllByUser(user);
    assertThat(response.widgets()).isEmpty();
  }

  @Test
  @DisplayName("PUT com widget repetido e rejeitado sem tocar no banco")
  void replaceWithDuplicateIdIsRejected() {
    DashboardWidgetsRequest request =
        new DashboardWidgetsRequest(
            List.of(
                new DashboardWidgetItem("NEW_CLIENTS", true, 0),
                new DashboardWidgetItem(" NEW_CLIENTS ", false, 1)));

    assertThatThrownBy(() -> service.replace(identity, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("NEW_CLIENTS");
    verify(repository, never()).deleteAllByUser(user);
  }
}
