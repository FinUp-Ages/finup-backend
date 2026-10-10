package br.com.finup.controller;

import br.com.finup.dto.ConversationMessageResponse;
import br.com.finup.dto.ConversationResponse;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Histórico de conversas do usuário autenticado com o assistente.
 *
 * <p>Nenhum endpoint recebe userId do cliente — a identidade vem sempre do {@link
 * AuthenticatedIdentityResolver}.
 */
@RestController
@RequestMapping("/api/v1/assistant/conversations")
@Tag(name = "Assistant", description = "Histórico de conversas do assistente")
public class ConversationController {

  private final ConversationService conversationService;
  private final AuthenticatedIdentityResolver authenticatedIdentityResolver;

  public ConversationController(
      ConversationService conversationService,
      AuthenticatedIdentityResolver authenticatedIdentityResolver) {
    this.conversationService = conversationService;
    this.authenticatedIdentityResolver = authenticatedIdentityResolver;
  }

  @GetMapping
  @Operation(
      summary = "Lista o histórico de conversas do usuário autenticado",
      description = "Ordenado por data de atualização decrescente.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou incompleta",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Usuário não encontrado",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<List<ConversationResponse>> findAll() {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    return ResponseEntity.ok(conversationService.findHistory(identity));
  }

  @GetMapping("/{id}/messages")
  @Operation(
      summary = "Lista as mensagens de uma conversa do usuário autenticado",
      description = "Em ordem cronológica. O texto vem do armazenamento de transcripts (S3).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Mensagens retornadas com sucesso"),
    @ApiResponse(
        responseCode = "401",
        description = "Identidade autenticada ausente ou incompleta",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Conversa inexistente ou de outro usuário",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "502",
        description = "Armazenamento do histórico indisponível",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<List<ConversationMessageResponse>> findMessages(@PathVariable UUID id) {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    return ResponseEntity.ok(conversationService.findMessages(identity, id));
  }
}
