package br.com.finup.mapper;

import br.com.finup.dto.FinancialProfileAnswerOptionResponse;
import br.com.finup.dto.FinancialProfileQuestionResponse;
import br.com.finup.model.FinancialProfileAnswerOption;
import br.com.finup.model.FinancialProfileQuestion;
import java.util.List;

/** Conversao entre {@link FinancialProfileQuestion} e os DTOs da API. */
public final class FinancialProfileQuestionMapper {

  private FinancialProfileQuestionMapper() {}

  public static FinancialProfileQuestionResponse toResponse(FinancialProfileQuestion question) {
    List<FinancialProfileAnswerOptionResponse> options =
        question.getOptions().stream()
            .map(FinancialProfileQuestionMapper::toOptionResponse)
            .toList();
    return new FinancialProfileQuestionResponse(
        question.getId(), question.getCode(), question.getText(), options);
  }

  private static FinancialProfileAnswerOptionResponse toOptionResponse(
      FinancialProfileAnswerOption option) {
    return new FinancialProfileAnswerOptionResponse(
        option.getId(), option.getCode(), option.getText(), option.getWeight());
  }

  public static List<FinancialProfileQuestionResponse> toResponses(
      List<FinancialProfileQuestion> questions) {
    return questions.stream().map(FinancialProfileQuestionMapper::toResponse).toList();
  }
}
