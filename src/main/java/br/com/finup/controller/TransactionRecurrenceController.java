package br.com.finup.controller;

import br.com.finup.dto.CreateTransactionRecurrenceRequest;
import br.com.finup.dto.TransactionRecurrenceResponse;
import br.com.finup.mapper.TransactionRecurrenceMapper;
import br.com.finup.model.TransactionRecurrence;
import br.com.finup.service.TransactionRecurrenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Camada HTTP do cadastro e consulta de recorrencias de transacao. */
@RestController
@RequestMapping("/api/v1/transaction-recurrences")
@Tag(
    name = "Transaction Recurrences",
    description = "Cadastro e consulta de recorrencias de transacao")
public class TransactionRecurrenceController {

  private final TransactionRecurrenceService transactionRecurrenceService;

  public TransactionRecurrenceController(
      TransactionRecurrenceService transactionRecurrenceService) {
    this.transactionRecurrenceService = transactionRecurrenceService;
  }

  @PostMapping
  @Operation(summary = "Cadastra uma recorrencia de transacao")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Recorrencia cadastrada"),
    @ApiResponse(
        responseCode = "400",
        description = "Campos invalidos",
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
    TransactionRecurrence recurrence =
        transactionRecurrenceService.register(
            request.userId(),
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

  @GetMapping("/{id}")
  @Operation(summary = "Busca uma recorrencia de transacao pelo identificador")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Recorrencia encontrada"),
    @ApiResponse(
        responseCode = "404",
        description = "Recorrencia inexistente",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public TransactionRecurrenceResponse findById(@PathVariable UUID id) {
    return TransactionRecurrenceMapper.toResponse(transactionRecurrenceService.findById(id));
  }

  @GetMapping("/due")
  @Operation(
      summary = "Lista as recorrencias do usuario que devem gerar uma transacao na data informada")
  public List<TransactionRecurrenceResponse> findDue(
      @Parameter(description = "Identificador do usuario") @RequestParam UUID userId,
      @Parameter(description = "Data de referencia; hoje quando omitida")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    LocalDate reference = date != null ? date : LocalDate.now();
    return transactionRecurrenceService.findDueOn(userId, reference).stream()
        .map(TransactionRecurrenceMapper::toResponse)
        .toList();
  }
}
