package br.com.finup.dto;

import br.com.finup.model.FinancialProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Past;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corpo da Etapa 2 do cadastro: informacoes complementares de um usuario que ja existe.
 *
 * <p>Todos os campos sao opcionais — cada chamada atualiza so o que veio preenchido, o que permite
 * tanto o primeiro preenchimento quanto atualizacoes parciais depois.
 */
@Schema(description = "Informacoes complementares do usuario")
public record UpdateUserAdditionalInfoRequest(
    @Schema(description = "Data de nascimento", example = "1998-04-12")
        @Past(message = "deve ser uma data no passado")
        LocalDate birthDate,
    @Schema(description = "Renda mensal", example = "3500.00")
        @DecimalMin(value = "0.0", message = "nao pode ser negativa")
        BigDecimal monthlyIncome,
    @Schema(description = "Perfil financeiro do usuario") FinancialProfile financialProfile) {}
