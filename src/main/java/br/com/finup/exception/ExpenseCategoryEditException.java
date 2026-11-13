package br.com.finup.exception;

/** A edicao de categoria deste fluxo se aplica apenas a despesas. */
public class ExpenseCategoryEditException extends BusinessException {

  public ExpenseCategoryEditException() {
    super("A edicao de categoria esta disponivel apenas para despesas.");
  }
}
