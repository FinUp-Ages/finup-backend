package br.com.finup.model;

/**
 * Status derivado de uma pergunta da trilha de estudos (Contrato C6).
 *
 * <p>O status nunca é gravado em coluna do banco de dados, sendo sempre calculado a partir do
 * estado das colunas {@code published} e {@code draft}:
 *
 * <ul>
 *   <li>{@code DRAFT}: quando {@code published} é nulo (nunca publicada).
 *   <li>{@code PUBLISHED}: quando publicada e o {@code draft} é idêntico ao {@code published} (sem
 *       rascunho pendente).
 *   <li>{@code PUBLISHED_WITH_CHANGES}: quando publicada e com edição não publicada no {@code
 *       draft}.
 * </ul>
 */
public enum QuestionStatus {
  DRAFT,
  PUBLISHED,
  PUBLISHED_WITH_CHANGES
}
