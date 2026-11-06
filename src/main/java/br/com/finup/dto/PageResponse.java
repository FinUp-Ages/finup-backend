package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Pagina de resultados, no mesmo formato para qualquer listagem paginada da API — content, page,
 * size, totalElements e totalPages.
 */
@Schema(description = "Pagina de resultados")
public record PageResponse<T>(
    @Schema(description = "Itens desta pagina") List<T> content,
    @Schema(description = "Pagina atual, baseada em zero", example = "0") int page,
    @Schema(description = "Tamanho da pagina", example = "20") int size,
    @Schema(description = "Total de itens em todas as paginas", example = "120") long totalElements,
    @Schema(description = "Total de paginas", example = "6") int totalPages) {

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
