package br.com.finup.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Resposta de 403 quando o usuario esta autenticado, mas a rota exige um grupo que ele nao tem.
 *
 * <p>Acontece no filtro do Spring Security, antes de qualquer controller, entao o {@code
 * ApiExceptionHandler} nao alcanca. E o par do {@link ProblemDetailAuthenticationEntryPoint}: mesmo
 * formato e mesmos campos ({@code timestamp}, {@code traceId}). O detalhe e generico de proposito:
 * nao diz qual grupo falta.
 *
 * <p>A tentativa fica no log em {@code WARN}, com o {@code sub} de quem tentou: producao roda em
 * {@code INFO}, e e esse log que mostra quem tentou entrar nas rotas de admin. Nao rebaixe o nivel.
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailAccessDeniedHandler.class);
  private static final URI TYPE_FORBIDDEN = URI.create("https://finup.com.br/errors/forbidden");

  private final ObjectMapper objectMapper;

  public ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "Voce nao tem permissao para acessar este recurso.");
    problem.setTitle(HttpStatus.FORBIDDEN.getReasonPhrase());
    problem.setType(TYPE_FORBIDDEN);
    problem.setProperty("timestamp", Instant.now());
    problem.setProperty("traceId", UUID.randomUUID().toString());
    log.warn(
        "Acesso negado [traceId={}]: {} {} por sub={}",
        problem.getProperties().get("traceId"),
        request.getMethod(),
        request.getRequestURI(),
        request.getRemoteUser());

    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}
