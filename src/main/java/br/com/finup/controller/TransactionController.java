package br.com.finup.controller;

import br.com.finup.dto.CreateTransactionRequest;
import br.com.finup.dto.TransactionResponse;
import br.com.finup.mapper.TransactionMapper;
import br.com.finup.model.Transaction;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.TransactionService;
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
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Camada HTTP do cadastro de transacoes financeiras.
 *
 * <p>Nenhum endpoint recebe userId do cliente — a identidade vem sempre do {@link
 * AuthenticatedIdentityResolver}.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Cadastro de transacoes financeiras")
public class TransactionController {

  private final TransactionService transactionService;
  private final AuthenticatedIdentityResolver authenticatedIdentityResolver;

  public TransactionController(
      TransactionService transactionService,
      AuthenticatedIdentityResolver authenticatedIdentityResolver) {
    this.transactionService = transactionService;
    this.authenticatedIdentityResolver = authenticatedIdentityResolver;
  }

  @PostMapping
  @Operation(summary = "Registra uma transacao financeira para o usuario autenticado")
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
    @ApiResponse(responseCode = "201", description = "Transacao cadastrada"),
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
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<TransactionResponse> register(
      @Valid @RequestBody CreateTransactionRequest request) {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    Transaction transaction =
        transactionService.register(
            identity,
            request.categoryId(),
            request.paymentMethodId(),
            request.type(),
            request.description(),
            request.amount(),
            request.transactionDate(),
            request.isRecurring(),
            request.recurrenceFrequency(),
            request.lastOccurrenceDateTime());
    TransactionResponse response = TransactionMapper.toResponse(transaction);
    URI location = URI.create("/api/v1/transactions/%s".formatted(response.id()));
    return ResponseEntity.created(location).body(response);
  }
}
