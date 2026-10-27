package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Usuario listado no painel administrativo. Nao existe "plano" no back — o front mostra "—" quando
 * o campo nao vem.
 */
@Schema(description = "Usuario listado no painel administrativo")
public record AdminUserResponse(
    @Schema(description = "Identificador gerado pelo servidor") UUID id,
    @Schema(description = "Nome completo") String name,
    @Schema(description = "E-mail") String email,
    @Schema(description = "Instante do cadastro, em UTC") Instant createdAt,
    @Schema(
            description =
                "Instante do ultimo acesso via GET /api/v1/users/me, em UTC; nulo se o usuario"
                    + " nunca acessou")
        Instant lastSeenAt,
    @Schema(description = "ACTIVE se lastSeenAt caiu nos ultimos 7 dias") UserStatus status) {}
