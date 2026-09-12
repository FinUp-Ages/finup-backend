package br.com.finup.mapper;

import br.com.finup.dto.TransactionResponse;
import br.com.finup.model.Transaction;

/** Converte a entidade de transacao para o contrato publico da API. */
public final class TransactionMapper {

  private TransactionMapper() {}

  public static TransactionResponse toResponse(Transaction transaction) {
    return new TransactionResponse(
        transaction.getId(),
        transaction.getUserId(),
        transaction.getCategoryId(),
        transaction.getPaymentMethodId(),
        transaction.getType(),
        transaction.getDescription(),
        transaction.getAmount(),
        transaction.getTransactionDate(),
        transaction.isRecurring(),
        transaction.getCreatedAt(),
        transaction.getUpdatedAt());
  }
}
