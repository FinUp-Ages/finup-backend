package br.com.finup.exception;

import java.util.List;

/**
 * Respostas da triagem reprovadas na validação. Carrega o campo e o motivo de cada falha, e o
 * {@code ApiExceptionHandler} devolve isso em {@code fields}, como nos demais 400 de validação. Os
 * motivos nunca citam o valor enviado: a resposta é dado financeiro pessoal.
 */
public class InvalidTriageAnswersException extends RuntimeException {

  private final transient List<ApiExceptionHandler.InvalidField> fields;

  public InvalidTriageAnswersException(List<ApiExceptionHandler.InvalidField> fields) {
    super("Uma ou mais respostas estao invalidas.");
    this.fields = List.copyOf(fields);
  }

  public List<ApiExceptionHandler.InvalidField> getFields() {
    return fields;
  }
}
