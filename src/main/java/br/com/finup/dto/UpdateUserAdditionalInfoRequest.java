package br.com.finup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corpo das Etapas 2 e 3 do cadastro: informacoes complementares de um usuario que ja existe.
 *
 * <p>Todos os campos sao opcionais — cada chamada atualiza so o que veio preenchido, o que permite
 * tanto o primeiro preenchimento quanto atualizacoes parciais depois.
 */
@Schema(description = "Informacoes complementares do usuario")
public record UpdateUserAdditionalInfoRequest(
    @Schema(description = "Telefone em E.164", example = "+5511999998888")
        @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "deve estar no formato E.164")
        String phone,
    @Schema(description = "Profissao", example = "Analista de sistemas")
        @Size(max = 255, message = "deve ter no maximo 255 caracteres")
        @Pattern(regexp = "^(?!\\s*$).*", message = "nao pode ser vazia")
        String profession,
    @Schema(description = "Data de nascimento", example = "2000-01-31")
        @Past(message = "deve ser uma data no passado")
        LocalDate birthDate,
    @Schema(description = "Renda mensal", example = "3500.00")
        @DecimalMin(value = "0.0", message = "nao pode ser negativa")
        @Digits(integer = 10, fraction = 2, message = "deve ter no maximo 10 inteiros e 2 decimais")
        BigDecimal monthlyIncome) {}
