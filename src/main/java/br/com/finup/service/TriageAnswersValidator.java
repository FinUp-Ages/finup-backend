package br.com.finup.service;

import br.com.finup.dto.TriageQuestionResponse;
import br.com.finup.dto.TriageQuestionResponse.Option;
import br.com.finup.exception.ApiExceptionHandler.InvalidField;
import br.com.finup.exception.InvalidTriageAnswersException;
import br.com.finup.model.IncomeExpenseRelation;
import br.com.finup.model.SpendingHabit;
import br.com.finup.model.TriageAnswers;
import br.com.finup.model.TriageAnswers.DebtType;
import br.com.finup.model.TriageAnswers.EmergencyReserve;
import br.com.finup.model.TriageAnswers.IncomeType;
import br.com.finup.model.TriageAnswers.MarketDropReaction;
import br.com.finup.model.TriageAnswers.RiskyHabit;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Valida o envio contra a versão atual do questionário, e só devolve respostas tipadas se tudo
 * estiver certo: pergunta desconhecida, obrigatória ausente, opção inválida, valor fora da faixa ou
 * versão antiga viram 400 com o campo. Reporta todas as falhas de uma vez, e nenhuma mensagem cita
 * o valor recebido.
 */
@Component
public class TriageAnswersValidator {

  /** Respostas aprovadas: tipadas para as regras e normalizadas para gravar e devolver. */
  public record Validated(TriageAnswers typed, Map<String, Object> normalized) {}

  public Validated validate(int version, Map<String, JsonNode> answers) {
    List<InvalidField> errors = new ArrayList<>();
    if (version != TriageQuestionnaire.VERSION) {
      errors.add(
          new InvalidField(
              "version", "versao desatualizada; a atual e a " + TriageQuestionnaire.VERSION));
    }

    Set<String> known =
        TriageQuestionnaire.questions().stream()
            .map(TriageQuestionResponse::id)
            .collect(Collectors.toSet());
    answers.keySet().stream()
        .filter(id -> !known.contains(id))
        .sorted()
        .forEach(id -> errors.add(new InvalidField(field(id), "pergunta desconhecida")));

    Map<String, Object> normalized = new LinkedHashMap<>();
    for (TriageQuestionResponse question : TriageQuestionnaire.questions()) {
      if (!applies(question, normalized)) {
        continue;
      }
      JsonNode node = answers.get(question.id());
      if (node == null || node.isNull()) {
        if (question.required()) {
          errors.add(new InvalidField(field(question.id()), "e obrigatoria"));
        }
        continue;
      }
      Object value = parse(question, node, errors);
      if (value != null) {
        normalized.put(question.id(), value);
      }
    }

    if (!errors.isEmpty()) {
      throw new InvalidTriageAnswersException(errors);
    }
    return new Validated(typed(normalized), normalized);
  }

  /** A pergunta condicional só vale se a que a liga já foi respondida com o valor esperado. */
  private static boolean applies(TriageQuestionResponse question, Map<String, Object> accepted) {
    var when = question.requiredWhen();
    return when == null || Boolean.valueOf(when.equals()).equals(accepted.get(when.questionId()));
  }

  private static Object parse(TriageQuestionResponse q, JsonNode node, List<InvalidField> errors) {
    String field = field(q.id());
    switch (q.type()) {
      case BOOLEAN -> {
        if (node.isBoolean()) {
          return node.booleanValue();
        }
        errors.add(new InvalidField(field, "deve ser verdadeiro ou falso"));
      }
      case NUMBER -> {
        return parseNumber(q, node, field, errors);
      }
      case SINGLE_CHOICE -> {
        if (node.isTextual() && isOption(q, node.textValue())) {
          return node.textValue();
        }
        errors.add(new InvalidField(field, "opcao invalida"));
      }
      case MULTIPLE_CHOICE -> {
        return parseMultiple(q, node, field, errors);
      }
    }
    return null;
  }

  private static Object parseNumber(
      TriageQuestionResponse q, JsonNode node, String field, List<InvalidField> errors) {
    if (!node.isNumber()) {
      errors.add(new InvalidField(field, "deve ser um numero"));
      return null;
    }
    BigDecimal value = node.decimalValue();
    if (value.stripTrailingZeros().scale() > q.decimals()) {
      errors.add(new InvalidField(field, "deve ter no maximo " + q.decimals() + " casas decimais"));
      return null;
    }
    if (value.compareTo(q.min()) < 0 || value.compareTo(q.max()) > 0) {
      errors.add(
          new InvalidField(
              field,
              "deve estar entre " + q.min().toPlainString() + " e " + q.max().toPlainString()));
      return null;
    }
    return q.decimals() == 0 ? (Object) value.intValueExact() : value;
  }

  private static Object parseMultiple(
      TriageQuestionResponse q, JsonNode node, String field, List<InvalidField> errors) {
    if (!node.isArray()) {
      errors.add(new InvalidField(field, "deve ser uma lista de opcoes"));
      return null;
    }
    Set<String> chosen = new LinkedHashSet<>();
    for (JsonNode item : node) {
      if (!item.isTextual() || !isOption(q, item.textValue()) || !chosen.add(item.textValue())) {
        errors.add(new InvalidField(field, "opcao invalida ou repetida"));
        return null;
      }
    }
    return List.copyOf(chosen);
  }

  private static boolean isOption(TriageQuestionResponse q, String value) {
    return q.options().stream().map(Option::value).anyMatch(value::equals);
  }

  private static String field(String questionId) {
    return "answers." + questionId;
  }

  private static TriageAnswers typed(Map<String, Object> a) {
    boolean hasDebt = (boolean) a.get(TriageQuestionnaire.HAS_DEBT);
    boolean invests = (boolean) a.get(TriageQuestionnaire.INVESTS);
    return new TriageAnswers(
        IncomeType.valueOf(text(a, TriageQuestionnaire.INCOME_TYPE)),
        (int) a.get(TriageQuestionnaire.CHILDREN_COUNT),
        (int) a.get(TriageQuestionnaire.DEPENDENTS_COUNT),
        IncomeExpenseRelation.valueOf(text(a, TriageQuestionnaire.INCOME_EXPENSE_RELATION)),
        (BigDecimal) a.get(TriageQuestionnaire.MONTHLY_EXPENSES),
        SpendingHabit.valueOf(text(a, TriageQuestionnaire.SPENDING_HABIT)),
        hasDebt,
        hasDebt ? enums(DebtType.class, a, TriageQuestionnaire.DEBT_TYPES) : Set.of(),
        (BigDecimal) a.get(TriageQuestionnaire.DEBT_TOTAL_AMOUNT),
        (boolean) a.get(TriageQuestionnaire.LATE_PAYMENTS_6M),
        EmergencyReserve.valueOf(text(a, TriageQuestionnaire.EMERGENCY_RESERVE)),
        invests,
        (BigDecimal) a.get(TriageQuestionnaire.INVESTED_AMOUNT),
        (String) a.get(TriageQuestionnaire.INVESTMENT_TIME),
        strings(a, TriageQuestionnaire.INVESTMENT_INTERESTS),
        text(a, TriageQuestionnaire.MAIN_GOAL),
        MarketDropReaction.valueOf(text(a, TriageQuestionnaire.MARKET_DROP_REACTION)),
        enums(RiskyHabit.class, a, TriageQuestionnaire.RISKY_HABITS));
  }

  private static String text(Map<String, Object> a, String id) {
    return (String) a.get(id);
  }

  @SuppressWarnings("unchecked")
  private static Set<String> strings(Map<String, Object> a, String id) {
    return new LinkedHashSet<>((List<String>) a.getOrDefault(id, List.of()));
  }

  private static <E extends Enum<E>> Set<E> enums(Class<E> type, Map<String, Object> a, String id) {
    Set<E> result = EnumSet.noneOf(type);
    strings(a, id).forEach(value -> result.add(Enum.valueOf(type, value)));
    return result;
  }
}
