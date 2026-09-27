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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Resposta de 401 quando o token falta ou e recusado.
 *
 * <p>Isto acontece no filtro do Spring Security, antes de chegar a qualquer controller — por isso o
 * {@code ApiExceptionHandler} nao alcanca. Sem esta classe o cliente receberia um 401 de corpo
 * vazio, fora do contrato de erro da API. Os campos sao os mesmos do handler ({@code timestamp},
 * {@code traceId}).
 *
 * <p>O cabecalho {@code WWW-Authenticate} (com o motivo da recusa, ex.: token expirado) continua
 * sendo o padrao do Spring, via {@link BearerTokenAuthenticationEntryPoint}. O detalhe devolvido no
 * corpo e generico de proposito: nao ajuda quem esta testando tokens forjados.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final Logger log =
      LoggerFactory.getLogger(ProblemDetailAuthenticationEntryPoint.class);
  private static final URI TYPE_UNAUTHORIZED =
      URI.create("https://finup.com.br/errors/unauthorized");

  private final BearerTokenAuthenticationEntryPoint bearerEntryPoint =
      new BearerTokenAuthenticationEntryPoint();
  private final ObjectMapper objectMapper;

  public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    bearerEntryPoint.commence(request, response, authException);

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED, "Token de acesso ausente, invalido ou expirado.");
    problem.setTitle(HttpStatus.UNAUTHORIZED.getReasonPhrase());
    problem.setType(TYPE_UNAUTHORIZED);
    problem.setProperty("timestamp", Instant.now());
    problem.setProperty("traceId", UUID.randomUUID().toString());
    log.debug(
        "Requisicao recusada [traceId={}]: {}",
        problem.getProperties().get("traceId"),
        authException.getMessage());

    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}
