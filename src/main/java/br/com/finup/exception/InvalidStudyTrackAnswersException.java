package br.com.finup.exception;

import org.springframework.http.HttpStatus;

public class InvalidStudyTrackAnswersException extends BusinessException {
  public InvalidStudyTrackAnswersException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
