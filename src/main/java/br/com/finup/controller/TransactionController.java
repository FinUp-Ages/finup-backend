package br.com.finup.controller;

import br.com.finup.dto.CreateTransactionRequest;
import br.com.finup.dto.TransactionPageResponse;
import br.com.finup.dto.TransactionResponse;
import br.com.finup.mapper.TransactionMapper;
import br.com.finup.model.Transaction;
import br.com.finup.model.TransactionType;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Camada HTTP do cadastro de transacoes financeiras.
 *
 * <p>Nenhum endpoint recebe userId do cliente — a identidade vem sempre do {@link
 * AuthenticatedIdentityResolver}.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Cadastro e consulta de transacoes financeiras")
public class TransactionController {

  private final TransactionService transactionService;
  private final AuthenticatedIdentityResolver authenticatedIdentityResolver;

  public TransactionController(
      TransactionService transactionService,
      AuthenticatedIdentityResolver authenticatedIdentityResolver) {
    this.transactionService = transactionService;
    this.authenticatedIdentityResolver = authenticatedIdentityResolver;
  }

  @GetMapping
  @Operation(
      summary = "Lista as transacoes do usuario autenticado",
      description =
          "Paginada, da mais recente para a mais antiga. Inclui as transacoes avulsas, as que abrem"
              + " uma serie recorrente e as ocorrencias geradas das series (recurrenceOriginId"
              + " aponta para a original). Todos os filtros sao opcionais e combinados com E.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Pagina retornada com sucesso"),
    @ApiResponse(
        responseCode = "400",
        description = "Filtro invalido (ex.: startDate depois de endDate, size acima de 100)",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou incompleta",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Usuario ainda nao criado (Etapa 1 nao foi feita)",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "501",
        description = "Contrato publicado, ainda nao implementado",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public TransactionPageResponse list(
      @Parameter(description = "Inicio do periodo, inclusive (yyyy-MM-dd)")
          @RequestParam(required = false)
          LocalDate startDate,
      @Parameter(description = "Fim do periodo, inclusive (yyyy-MM-dd)")
          @RequestParam(required = false)
          LocalDate endDate,
      @Parameter(description = "Filtra por tipo da transacao") @RequestParam(required = false)
          TransactionType type,
      @Parameter(description = "Filtra por categoria") @RequestParam(required = false)
          UUID categoryId,
      @Parameter(description = "Pagina, a partir de 0") @RequestParam(defaultValue = "0") @Min(0)
          int page,
      @Parameter(description = "Itens por pagina, de 1 a 100")
          @RequestParam(defaultValue = "20")
          @Min(1)
          @Max(100)
          int size) {
    throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
  }

  @PostMapping
  @Operation(summary = "Registra uma transacao financeira para o usuario autenticado")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Transacao cadastrada"),
    @ApiResponse(
        responseCode = "400",
        description =
            "Campos invalidos: categoryId, type, amount (maior que zero, ate 10 inteiros e 2"
                + " decimais), transactionDate e isRecurring sao obrigatorios; description aceita"
                + " ate 255 caracteres; recurrenceFrequency e obrigatoria quando isRecurring e true",
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
        description =
            "Tipo da categoria diferente do tipo da transacao, ou isRecurring e"
                + " recurrenceFrequency se contradizem",
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
            request.recurrenceFrequency());
    TransactionResponse response = TransactionMapper.toResponse(transaction);
    URI location = URI.create("/api/v1/transactions/%s".formatted(response.id()));
    return ResponseEntity.created(location).body(response);
  }
}
