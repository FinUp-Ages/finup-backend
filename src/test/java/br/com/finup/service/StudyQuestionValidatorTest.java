package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.finup.dto.StudyQuestionRequest;
import br.com.finup.exception.InvalidStudyQuestionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

class StudyQuestionValidatorTest {

  private final StudyQuestionValidator validator = new StudyQuestionValidator();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @ParameterizedTest
  @MethodSource("validContents")
  void acceptsContentForEachType(String json) throws Exception {
    StudyQuestionRequest request = objectMapper.readValue(json, StudyQuestionRequest.class);
    assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
  }

  static Stream<String> validContents() {
    return Stream.of(
        """
        {"type":"FILL_BLANK","title":"CDB","statement":"O CDB rende ___ do CDI",
         "answers":["100%","cem por cento"],"explanation":""}
        """,
        """
        {"type":"MULTIPLE_CHOICE","statement":"Qual opção?",
         "options":[{"text":"A","correct":true},{"text":"B","correct":false}]}
        """,
        """
        {"type":"MULTIPLE_CHOICE","statement":"Quais opções?",
         "options":[{"text":"A","correct":true},{"text":"B","correct":true}]}
        """,
        """
        {"type":"LEARNING","title":"Reserva","body":"## Conceito\\n\\n**Poupar**"}
        """);
  }

  @ParameterizedTest
  @MethodSource("invalidContents")
  void rejectsInvalidContentWith400(String json) throws Exception {
    StudyQuestionRequest request = objectMapper.readValue(json, StudyQuestionRequest.class);
    assertThatThrownBy(() -> validator.validate(request))
        .isInstanceOfSatisfying(
            InvalidStudyQuestionException.class,
            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  static Stream<String> invalidContents() {
    String fill =
        "{\"type\":\"FILL_BLANK\",\"title\":\"CDB\",\"statement\":\"Rende ___\",\"answers\":[\"100%\"]}";
    String choice =
        "{\"type\":\"MULTIPLE_CHOICE\",\"statement\":\"Qual?\",\"options\":[{\"text\":\"A\",\"correct\":true}]}";
    return Stream.of(
        "{}",
        fill.replace("\"CDB\"", "null"),
        fill.replace("\"CDB\"", "\"   \""),
        fill.replace("\"Rende ___\"", "null"),
        fill.replace("\"Rende ___\"", "\"   \""),
        fill.replace("[\"100%\"]", "null"),
        fill.replace("[\"100%\"]", "[]"),
        fill.replace("[\"100%\"]", "[null]"),
        fill.replace("[\"100%\"]", "[\"   \"]"),
        fill.replace("[\"100%\"]", "[\"100%\",\"\"]"),
        choice.replace("\"Qual?\"", "\" \""),
        "{\"type\":\"MULTIPLE_CHOICE\",\"statement\":\"Qual?\"}",
        "{\"type\":\"MULTIPLE_CHOICE\",\"statement\":\"Qual?\",\"options\":[]}",
        "{\"type\":\"MULTIPLE_CHOICE\",\"statement\":\"Qual?\",\"options\":[null]}",
        choice.replace("\"A\"", "null"),
        choice.replace("\"A\"", "\"  \""),
        choice.replace("true", "null"),
        choice.replace("true", "false"),
        "{\"type\":\"LEARNING\",\"title\":\"Reserva\"}",
        "{\"type\":\"LEARNING\",\"body\":\"Texto\"}",
        "{\"type\":\"LEARNING\",\"title\":\"  \",\"body\":\"Texto\"}",
        "{\"type\":\"LEARNING\",\"title\":\"Reserva\",\"body\":\"  \"}");
  }
}
