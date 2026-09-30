package br.com.finup.exception;

import br.com.finup.model.TransactionType;
import org.springframework.http.HttpStatus;

/**
 * Categoria cujo tipo nao corresponde ao tipo da transacao que a referencia.
 *
 * <p>Categoria e transacao guardam o mesmo enum ({@link TransactionType}), entao uma categoria de
 * despesa nunca deve classificar uma receita — o lancamento entraria no lado errado do balanco.
 */
public class IncompatibleTransactionCategoryException extends BusinessException {

  public IncompatibleTransactionCategoryException(
      TransactionType categoryType, TransactionType transactionType) {
    super(
        "Categoria do tipo %s nao pode ser usada em transacao do tipo %s"
            .formatted(categoryType, transactionType),
        HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
