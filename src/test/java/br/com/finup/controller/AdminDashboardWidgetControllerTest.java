package br.com.finup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.dto.DashboardWidgetItem;
import br.com.finup.dto.DashboardWidgetsResponse;
import br.com.finup.exception.BusinessException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.DashboardWidgetService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AdminDashboardWidgetController.class)
class AdminDashboardWidgetControllerTest {

  private static final String URL = "/api/v1/admin/dashboard/widgets";
  private static final AuthenticatedIdentity IDENTITY =
      new AuthenticatedIdentity("mock-sub", "Ana Souza", "ana@exemplo.com");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DashboardWidgetService dashboardWidgetService;
  @MockitoBean private AuthenticatedIdentityResolver authenticatedIdentityResolver;

  @BeforeEach
  void mockIdentity() {
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(IDENTITY);
  }

  @Test
  @DisplayName("GET devolve 200 com os widgets")
  void getReturns200() throws Exception {
    when(dashboardWidgetService.find(IDENTITY))
        .thenReturn(
            new DashboardWidgetsResponse(List.of(new DashboardWidgetItem("NEW_CLIENTS", true, 0))));

    mockMvc
        .perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.widgets[0].id").value("NEW_CLIENTS"))
        .andExpect(jsonPath("$.widgets[0].enabled").value(true))
        .andExpect(jsonPath("$.widgets[0].position").value(0));
  }

  @Test
  @DisplayName("GET sem configuracao devolve 200 com lista vazia")
  void getWithoutSettingsReturnsEmptyList() throws Exception {
    when(dashboardWidgetService.find(IDENTITY)).thenReturn(new DashboardWidgetsResponse(List.of()));

    mockMvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.widgets").isEmpty());
  }

  @Test
  @DisplayName("GET de usuario inexistente devolve 404")
  void getUnknownUserReturns404() throws Exception {
    when(dashboardWidgetService.find(IDENTITY)).thenThrow(new ResourceNotFoundException("x"));

    mockMvc.perform(get(URL)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PUT valido devolve 200 com a configuracao gravada")
  void putReturns200() throws Exception {
    when(dashboardWidgetService.replace(eq(IDENTITY), any()))
        .thenReturn(
            new DashboardWidgetsResponse(List.of(new DashboardWidgetItem("NEW_CLIENTS", true, 0))));

    mockMvc
        .perform(
            put(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"widgets\":[{\"id\":\"NEW_CLIENTS\",\"enabled\":true,\"position\":0}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.widgets[0].id").value("NEW_CLIENTS"));
  }

  @Test
  @DisplayName("PUT sem a lista widgets devolve 400")
  void putWithoutWidgetsReturns400() throws Exception {
    mockMvc
        .perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PUT com item invalido devolve 400")
  void putWithInvalidItemReturns400() throws Exception {
    mockMvc
        .perform(
            put(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"widgets\":[{\"id\":\"\",\"enabled\":null,\"position\":-1}]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PUT com widget repetido devolve 422")
  void putWithDuplicateReturns422() throws Exception {
    when(dashboardWidgetService.replace(eq(IDENTITY), any()))
        .thenThrow(new BusinessException("Widget repetido: NEW_CLIENTS"));

    mockMvc
        .perform(
            put(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"widgets\":[{\"id\":\"NEW_CLIENTS\",\"enabled\":true,\"position\":0},"
                        + "{\"id\":\"NEW_CLIENTS\",\"enabled\":true,\"position\":1}]}"))
        .andExpect(status().isUnprocessableEntity());
  }
}
