package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Pagina de transacoes do usuario, da mais recente para a mais antiga. */
@Schema(description = "Pagina de transacoes do usuario autenticado")
public record TransactionPageResponse(
    @Schema(description = "Transacoes da pagina, ordenadas por data decrescente")
        List<TransactionResponse> items,
    @Schema(description = "Pagina atual, a partir de 0", example = "0") int page,
    @Schema(description = "Tamanho solicitado da pagina", example = "20") int size,
    @Schema(description = "Total de transacoes que atendem aos filtros", example = "135")
        long totalItems,
    @Schema(description = "Total de paginas", example = "7") int totalPages) {}
