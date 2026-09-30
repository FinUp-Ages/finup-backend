package br.com.finup.service;

import br.com.finup.model.Category;
import br.com.finup.model.Transaction;
import br.com.finup.security.AuthenticatedIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;

/**
 * Uma acao que o assistente sabe executar a partir de texto livre.
 *
 * <p>E o ponto de extensao do assistente: para ensinar algo novo (ex.: consultar saldo, criar
 * categoria), basta criar um {@code @Component} que implemente esta interface. O {@link
 * AiAssistantService} o descobre sozinho, inclui {@link #promptInstructions()} no prompt e despacha
 * para {@link #execute} quando o modelo escolher {@link #name()}.
 */
public interface AiAction {

  /** Identificador devolvido pelo modelo no campo {@code action}. */
  String name();

  /** Trecho do prompt que explica ao modelo quando usar a acao e o formato JSON esperado. */
  String promptInstructions();

  /**
   * Executa a acao. O {@code payload} vem do modelo e nao e confiavel: valide tudo antes de usar.
   *
   * @throws br.com.finup.exception.BusinessException se a saida do modelo for inutilizavel
   */
  Result execute(Context context, JsonNode payload);

  /** O que a acao precisa saber sobre quem pediu. */
  record Context(
      AuthenticatedIdentity identity,
      List<Category> categories,
      String originalMessage,
      LocalDate today) {}

  /** Resultado para o cliente; {@code transaction} so vem preenchida por acoes que a criam. */
  record Result(String message, Transaction transaction) {}
}
