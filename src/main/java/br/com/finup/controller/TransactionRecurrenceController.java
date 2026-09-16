package br.com.finup.controller;

import br.com.finup.dto.CreateTransactionRecurrenceRequest;
import br.com.finup.dto.TransactionRecurrenceResponse;
import br.com.finup.mapper.TransactionRecurrenceMapper;
import br.com.finup.model.TransactionRecurrence;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.TransactionRecurrenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Camada HTTP do cadastro e consulta de recorrencias de transacao.
 *
 * <p>Nenhum endpoint recebe userId do cliente — a identidade vem sempre do {@link
 * AuthenticatedIdentityResolver}.
 */
@RestController
@RequestMapping("/api/v1/transaction-recurrences")
@Tag(
    name = "Transaction Recurrences",
    description = "Cadastro e consulta de recorrencias de transacao")
public class TransactionRecurrenceController {

  private final TransactionRecurrenceService transactionRecurrenceService;
  private final AuthenticatedIdentityResolver authenticatedIdentityResolver;

  public TransactionRecurrenceController(
      TransactionRecurrenceService transactionRecurrenceService,
      AuthenticatedIdentityResolver authenticatedIdentityResolver) {
    this.transactionRecurrenceService = transactionRecurrenceService;
    this.authenticatedIdentityResolver = authenticatedIdentityResolver;
  }

  @PostMapping
  @Operation(summary = "Cadastra uma recorrencia de transacao para o usuario autenticado")
  @Parameters({
    @Parameter(
        name = "X-Mock-Cognito-Sub",
        in = ParameterIn.HEADER,
        required = true,
        description = "Identificador (sub) da identidade autenticada — mock do Cognito real."),
    @Parameter(
        name = "X-Mock-Cognito-Email",
        in = ParameterIn.HEADER,
        required = true,
        description = "E-mail da identidade autenticada — mock do Cognito real."),
    @Parameter(
        name = "X-Mock-Cognito-Name",
        in = ParameterIn.HEADER,
        required = false,
        description = "Nome da identidade autenticada — mock do Cognito real. Opcional.")
  })
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Recorrencia cadastrada"),
    @ApiResponse(
        responseCode = "400",
        description = "Campos invalidos",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou incompleta",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Usuario, categoria ou meio de pagamento inexistente",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description = "Data de termino anterior a data de inicio",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<TransactionRecurrenceResponse> register(
      @Valid @RequestBody CreateTransactionRecurrenceRequest request) {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    TransactionRecurrence recurrence =
        transactionRecurrenceService.register(
            identity,
            request.categoryId(),
            request.paymentMethodId(),
            request.type(),
            request.description(),
            request.amount(),
            request.frequency(),
            request.dayOfMonth(),
            request.startDate(),
            request.endDate());
    TransactionRecurrenceResponse response = TransactionRecurrenceMapper.toResponse(recurrence);
    URI location = URI.create("/api/v1/transaction-recurrences/%s".formatted(response.id()));
    return ResponseEntity.created(location).body(response);
  }

  @GetMapping("/due")
  @Operation(
      summary =
          "Lista as recorrencias do usuario autenticado que devem gerar uma transacao na data"
              + " informada")
  @Parameters({
    @Parameter(
        name = "X-Mock-Cognito-Sub",
        in = ParameterIn.HEADER,
        required = true,
        description = "Identificador (sub) da identidade autenticada — mock do Cognito real."),
    @Parameter(
        name = "X-Mock-Cognito-Email",
        in = ParameterIn.HEADER,
        required = true,
        description = "E-mail da identidade autenticada — mock do Cognito real."),
    @Parameter(
        name = "X-Mock-Cognito-Name",
        in = ParameterIn.HEADER,
        required = false,
        description = "Nome da identidade autenticada — mock do Cognito real. Opcional.")
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou incompleta",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Usuario nao encontrado",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public List<TransactionRecurrenceResponse> findDue(
      @Parameter(description = "Data de referencia; hoje quando omitida")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    LocalDate reference = date != null ? date : LocalDate.now();
    return transactionRecurrenceService.findDueOn(identity, reference).stream()
        .map(TransactionRecurrenceMapper::toResponse)
        .toList();
  }
}
