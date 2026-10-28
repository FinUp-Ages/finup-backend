package br.com.finup.service;

import br.com.finup.dto.StudyQuestionRequest;
import br.com.finup.exception.InvalidStudyQuestionException;
import org.springframework.stereotype.Component;

/** Validação compartilhada pelo POST e PUT, sem acesso ao banco. */
@Component
public class StudyQuestionValidator {

  public void validate(StudyQuestionRequest request) {
    if (request.type() == null) {
      throw new InvalidStudyQuestionException("Informe o tipo da pergunta.");
    }
    switch (request.type()) {
      case FILL_BLANK -> {
        requireText(request.title(), "Informe o título.");
        requireText(request.statement(), "Informe o enunciado.");
        if (request.answers() == null
            || request.answers().isEmpty()
            || request.answers().stream().anyMatch(answer -> answer == null || answer.isBlank())) {
          throw new InvalidStudyQuestionException("Informe pelo menos uma resposta não vazia.");
        }
      }
      case MULTIPLE_CHOICE -> {
        requireText(request.statement(), "Informe o enunciado.");
        if (request.options() == null || request.options().isEmpty()) {
          throw new InvalidStudyQuestionException("Informe as alternativas da pergunta.");
        }
        for (StudyQuestionRequest.Option option : request.options()) {
          if (option == null) {
            throw new InvalidStudyQuestionException("Alternativa inválida.");
          }
          requireText(option.text(), "Informe o texto de cada alternativa.");
          if (option.correct() == null) {
            throw new InvalidStudyQuestionException("Informe se cada alternativa é correta.");
          }
        }
        if (request.options().stream().noneMatch(option -> option.correct())) {
          throw new InvalidStudyQuestionException("Informe pelo menos uma alternativa correta.");
        }
      }
      case LEARNING -> {
        requireText(request.title(), "Informe o título.");
        requireText(request.body(), "Informe o conteúdo educativo.");
      }
    }
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new InvalidStudyQuestionException(message);
    }
  }
}
