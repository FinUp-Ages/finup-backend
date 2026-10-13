package br.com.finup.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Corpo RFC 7807 dos erros que o Spring Security devolve no filtro, antes de qualquer controller:
 * 401 e 403. Os campos sao os mesmos do {@code ApiExceptionHandler} ({@code timestamp}, {@code
 * traceId}), que nao alcanca esse ponto da requisicao.
 */
final class SecurityProblemResponse {

  private SecurityProblemResponse() {}

  /** Devolve o {@link ProblemDetail} escrito, para quem chamou registrar o {@code traceId}. */
  static ProblemDetail write(
      HttpServletResponse response,
      ObjectMapper objectMapper,
      HttpStatus status,
      URI type,
      String detail)
      throws IOException {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    problem.setType(type);
    problem.setProperty("timestamp", Instant.now());
    problem.setProperty("traceId", UUID.randomUUID().toString());

    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), problem);
    return problem;
  }
}
