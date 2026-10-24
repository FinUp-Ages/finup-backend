package br.com.finup.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Resposta de categoria da trilha de estudos com contagens agregadas (Contrato C6).
 *
 * @param id Identificador único da categoria
 * @param name Nome obrigatório da categoria
 * @param description Descrição opcional da categoria
 * @param draftCount Quantidade de perguntas com rascunho pendente (DRAFT ou PUBLISHED_WITH_CHANGES)
 * @param publishedCount Quantidade de perguntas publicadas no ar (PUBLISHED ou
 *     PUBLISHED_WITH_CHANGES)
 * @param createdAt Data de criação
 * @param updatedAt Data da última atualização
 */
public record StudyCategoryResponse(
    UUID id,
    String name,
    String description,
    long draftCount,
    long publishedCount,
    Instant createdAt,
    Instant updatedAt) {}
