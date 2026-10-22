package br.com.finup.exception;

import org.springframework.http.HttpStatus;

/** Conteúdo de pergunta inválido. Traduzido pelo handler central para 400. */
public class InvalidStudyQuestionException extends BusinessException {

  public InvalidStudyQuestionException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
