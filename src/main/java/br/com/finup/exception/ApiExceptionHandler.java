package br.com.finup.exception;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Contrato de erro unico da API, em RFC 7807 ({@code application/problem+json}).
 *
 * <p>Toda resposta de erro tem os mesmos campos, e o cliente (web e mobile) consegue gerar um tipo
 * a partir do OpenAPI. Nunca devolva erro montado a mao dentro de um controller.
 *
 * <p>Estende {@link ResponseEntityExceptionHandler} de proposito: sem isso, o
 * {@code @ExceptionHandler(Exception.class)} abaixo capturaria tambem as excecoes do proprio Spring
 * MVC (rota inexistente, metodo nao suportado, media type errado) e devolveria 500 no lugar de
 * 404/405. A classe base ja traduz cada uma delas para o status correto em RFC 7807.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private static final URI TYPE_VALIDATION = URI.create("https://finup.com.br/errors/validation");
  private static final URI TYPE_BUSINESS = URI.create("https://finup.com.br/errors/business-rule");
  private static final URI TYPE_INTERNAL = URI.create("https://finup.com.br/errors/internal");

  /** Corpo invalido em endpoint anotado com {@code @Valid}. Lista campo a campo o que falhou. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {

    List<InvalidField> fields =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new InvalidField(e.getField(), e.getDefaultMessage()))
            .sorted(Comparator.comparing(InvalidField::field))
            .toList();

    ProblemDetail problem =
        buildProblem(HttpStatus.BAD_REQUEST, "Um ou mais campos estao invalidos.");
    problem.setTitle("Requisicao invalida");
    problem.setType(TYPE_VALIDATION);
    problem.setProperty("fields", fields);
    return ResponseEntity.badRequest().headers(headers).body(problem);
  }

  /** Regra de negocio violada. O status vem da propria excecao. */
  @ExceptionHandler(BusinessException.class)
  public ProblemDetail handleBusiness(BusinessException ex) {
    ProblemDetail problem = buildProblem(ex.getStatus(), ex.getMessage());
    problem.setTitle(ex.getStatus().getReasonPhrase());
    problem.setType(TYPE_BUSINESS);
    return problem;
  }

  /**
   * Rede de seguranca para o que nao foi previsto. A mensagem original nao vai para o cliente — so
   * para o log, junto do {@code traceId} que o cliente recebe, para dar rastreabilidade sem vazar
   * detalhe interno.
   */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    ProblemDetail problem =
        base(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Tente novamente mais tarde.");
    problem.setTitle("Erro interno");
    problem.setType(TYPE_INTERNAL);
    log.error("Erro nao tratado [traceId={}]", problem.getProperties().get("traceId"), ex);
    return problem;
  }

  private ProblemDetail buildProblem(HttpStatusCode status, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("timestamp", Instant.now());
    problem.setProperty("traceId", UUID.randomUUID().toString());
    return problem;
  }

  /** Um campo reprovado na validacao. */
  public record InvalidField(String field, String message) {}
}
