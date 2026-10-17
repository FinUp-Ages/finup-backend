package br.com.finup.dto;

import br.com.finup.security.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * O que o painel recebe sobre o administrador autenticado.
 *
 * <p>Separado do {@link UserResponse} de proposito: o painel so precisa saber quem entrou e com que
 * papel, e nao dos dados do cadastro financeiro. {@code id} e o mesmo de {@code GET
 * /api/v1/users/me}.
 */
@Schema(description = "Administrador autenticado no painel")
public record AdminMeResponse(
    @Schema(description = "Identificador do usuario, gerado pelo servidor") UUID id,
    @Schema(description = "Nome completo") String name,
    @Schema(description = "E-mail, sempre em minusculas") String email,
    @Schema(description = "Papel de acesso. A rota so responde a quem esta no grupo admin")
        Role role) {}
