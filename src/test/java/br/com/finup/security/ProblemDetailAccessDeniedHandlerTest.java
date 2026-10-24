package br.com.finup.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class ProblemDetailAccessDeniedHandlerTest {

  private final Logger handlerLogger =
      (Logger) LoggerFactory.getLogger(ProblemDetailAccessDeniedHandler.class);
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private final ProblemDetailAccessDeniedHandler handler =
      new ProblemDetailAccessDeniedHandler(Jackson2ObjectMapperBuilder.json().build());

  @BeforeEach
  void captureLogsAtProductionLevel() {
    // Producao roda com br.com.finup em INFO: o que nao aparece neste nivel nao aparece la.
    handlerLogger.setLevel(Level.INFO);
    logs.start();
    handlerLogger.addAppender(logs);
  }

  @AfterEach
  void releaseLogger() {
    handlerLogger.detachAppender(logs);
    handlerLogger.setLevel(null);
  }

  @Test
  @DisplayName("acesso negado fica no log em WARN, com o sub de quem tentou, no nivel de producao")
  void deniedAccessIsLoggedAtWarnWithTheSubject() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/me");
    request.setRemoteUser("cognito-sub-123");

    handler.handle(request, new MockHttpServletResponse(), new AccessDeniedException("negado"));

    assertThat(logs.list).hasSize(1);
    ILoggingEvent event = logs.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage())
        .contains("Acesso negado")
        .contains("GET /api/v1/admin/me")
        .contains("por sub=cognito-sub-123")
        .contains("traceId=");
  }

  @Test
  @DisplayName("o log tem o mesmo traceId que o cliente recebe no corpo do 403")
  void logCarriesTheSameTraceIdAsTheResponse() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/v1/admin/metrics/summary");
    request.setRemoteUser("cognito-sub-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.handle(request, response, new AccessDeniedException("negado"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    String traceId = com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.traceId");
    assertThat(logs.list.get(0).getFormattedMessage()).contains("traceId=" + traceId);
  }

  @Test
  @DisplayName("o corpo do 403 nao diz qual grupo falta")
  void responseDoesNotRevealTheRequiredGroup() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.handle(
        new MockHttpServletRequest("GET", "/api/v1/admin/me"),
        response,
        new AccessDeniedException("negado"));

    assertThat(response.getContentAsString()).doesNotContain("admin").doesNotContain("GROUP_");
  }
}
