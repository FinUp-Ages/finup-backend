package br.com.finup.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Resposta de 403 quando o token e valido mas nao da acesso a rota — hoje, quem chama {@code
 * /api/v1/admin/**} sem estar no grupo {@code admin} do Cognito.
 *
 * <p>Par do {@link ProblemDetailAuthenticationEntryPoint}: a recusa tambem acontece no filtro do
 * Spring Security, fora do alcance do {@code ApiExceptionHandler}, e sem esta classe o cliente
 * receberia um 403 de corpo vazio. O cabecalho {@code WWW-Authenticate} continua sendo o padrao do
 * Spring, via {@link BearerTokenAccessDeniedHandler}.
 *
 * <p>O detalhe nao diz qual grupo falta: quem nao e administrador nao precisa saber como o acesso e
 * concedido. A tentativa fica no log, com o {@code sub} de quem tentou.
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailAccessDeniedHandler.class);
  private static final URI TYPE_FORBIDDEN = URI.create("https://finup.com.br/errors/forbidden");

  private final BearerTokenAccessDeniedHandler bearerHandler = new BearerTokenAccessDeniedHandler();
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
    bearerHandler.handle(request, response, accessDeniedException);

    ProblemDetail problem =
        SecurityProblemResponse.write(
            response,
            objectMapper,
            HttpStatus.FORBIDDEN,
            TYPE_FORBIDDEN,
            "Voce nao tem permissao para acessar este recurso.");
    log.warn(
        "Acesso negado [traceId={}]: {} {} por sub={}",
        problem.getProperties().get("traceId"),
        request.getMethod(),
        request.getRequestURI(),
        request.getRemoteUser());
  }
}
