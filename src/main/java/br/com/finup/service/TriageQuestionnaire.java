package br.com.finup.service;

import br.com.finup.dto.TriageQuestionResponse;
import br.com.finup.dto.TriageQuestionResponse.Option;
import br.com.finup.dto.TriageQuestionResponse.RequiredWhen;
import br.com.finup.dto.TriageQuestionResponse.Type;
import java.math.BigDecimal;
import java.util.List;

/**
 * PROVISÓRIO: a lista final de perguntas (texto, tipo, opções e faixas) e o contrato ainda serão
 * fechados com o front; o que está aqui é a proposta, usada como mock até lá.
 *
 * <p>O questionário de triagem, versionado e definido no back: o app só renderiza. Mudou pergunta,
 * opção ou faixa, suba {@link #VERSION}: o envio com a versão antiga é recusado com 400 e o app
 * baixa o questionário novo.
 *
 * <p>Os valores das opções que o código interpreta (rendas, reservas, hábitos) são os nomes dos
 * enums de {@code TriageAnswers} e do domínio; mudar um deles exige mudar o enum junto.
 */
public final class TriageQuestionnaire {

  public static final int VERSION = 1;

  public static final String INCOME_TYPE = "INCOME_TYPE";
  public static final String CHILDREN_COUNT = "CHILDREN_COUNT";
  public static final String DEPENDENTS_COUNT = "DEPENDENTS_COUNT";
  public static final String INCOME_EXPENSE_RELATION = "INCOME_EXPENSE_RELATION";
  public static final String MONTHLY_EXPENSES = "MONTHLY_EXPENSES";
  public static final String SPENDING_HABIT = "SPENDING_HABIT";
  public static final String HAS_DEBT = "HAS_DEBT";
  public static final String DEBT_TYPES = "DEBT_TYPES";
  public static final String DEBT_TOTAL_AMOUNT = "DEBT_TOTAL_AMOUNT";
  public static final String LATE_PAYMENTS_6M = "LATE_PAYMENTS_6M";
  public static final String EMERGENCY_RESERVE = "EMERGENCY_RESERVE";
  public static final String INVESTS = "INVESTS";
  public static final String INVESTED_AMOUNT = "INVESTED_AMOUNT";
  public static final String INVESTMENT_TIME = "INVESTMENT_TIME";
  public static final String INVESTMENT_INTERESTS = "INVESTMENT_INTERESTS";
  public static final String MAIN_GOAL = "MAIN_GOAL";
  public static final String MARKET_DROP_REACTION = "MARKET_DROP_REACTION";
  public static final String RISKY_HABITS = "RISKY_HABITS";

  private static final BigDecimal MAX_COUNT = BigDecimal.valueOf(20);
  private static final BigDecimal MAX_MONEY = BigDecimal.valueOf(100_000_000);

  private static final RequiredWhen WHEN_HAS_DEBT = new RequiredWhen(HAS_DEBT, true);
  private static final RequiredWhen WHEN_INVESTS = new RequiredWhen(INVESTS, true);

  private static final List<TriageQuestionResponse> QUESTIONS =
      List.of(
          choice(
              INCOME_TYPE,
              "Como é a sua renda?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("FIXED", "Fixa"),
              opt("VARIABLE", "Variável"),
              opt("MIXED", "Mista")),
          number(CHILDREN_COUNT, "Quantos filhos você tem?", MAX_COUNT, 0, null),
          number(
              DEPENDENTS_COUNT,
              "Quantas outras pessoas dependem financeiramente de você?",
              MAX_COUNT,
              0,
              null),
          choice(
              INCOME_EXPENSE_RELATION,
              "Você costuma gastar menos, o mesmo ou mais do que ganha?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("SPENDS_LESS", "Menos do que ganho"),
              opt("BALANCED", "O mesmo que ganho"),
              opt("SPENDS_MORE", "Mais do que ganho")),
          number(MONTHLY_EXPENSES, "Qual o seu gasto mensal médio?", MAX_MONEY, 2, null),
          choice(
              SPENDING_HABIT,
              "Como você descreve o seu hábito de gastos?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("CONTROLLED", "Controlado"),
              opt("MODERATE", "Moderado"),
              opt("IMPULSIVE", "Impulsivo")),
          bool(HAS_DEBT, "Você tem dívidas?", null),
          choice(
              DEBT_TYPES,
              "De que tipo são as suas dívidas?",
              Type.MULTIPLE_CHOICE,
              true,
              WHEN_HAS_DEBT,
              opt("CREDIT_CARD", "Cartão de crédito"),
              opt("OVERDRAFT", "Cheque especial"),
              opt("PERSONAL_LOAN", "Empréstimo pessoal"),
              opt("FINANCING", "Financiamento"),
              opt("OTHER", "Outra")),
          number(DEBT_TOTAL_AMOUNT, "Quanto você deve, no total?", MAX_MONEY, 2, WHEN_HAS_DEBT),
          bool(LATE_PAYMENTS_6M, "Você atrasou alguma conta nos últimos 6 meses?", null),
          choice(
              EMERGENCY_RESERVE,
              "Quanto você tem de reserva de emergência?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("NONE", "Nenhuma"),
              opt("UP_TO_3_MONTHS", "Até 3 meses de gastos"),
              opt("FROM_3_TO_6_MONTHS", "De 3 a 6 meses de gastos"),
              opt("OVER_6_MONTHS", "Mais de 6 meses de gastos")),
          bool(INVESTS, "Você investe?", null),
          number(
              INVESTED_AMOUNT, "Quanto você tem investido, no total?", MAX_MONEY, 2, WHEN_INVESTS),
          choice(
              INVESTMENT_TIME,
              "Há quanto tempo você investe?",
              Type.SINGLE_CHOICE,
              true,
              WHEN_INVESTS,
              opt("LESS_THAN_1_YEAR", "Menos de 1 ano"),
              opt("ONE_TO_THREE_YEARS", "De 1 a 3 anos"),
              opt("OVER_3_YEARS", "Mais de 3 anos")),
          choice(
              INVESTMENT_INTERESTS,
              "Em que você tem interesse de investir?",
              Type.MULTIPLE_CHOICE,
              false,
              null,
              opt("SAVINGS", "Poupança"),
              opt("CDB", "CDB"),
              opt("TREASURY", "Tesouro Direto"),
              opt("FUNDS", "Fundos"),
              opt("REAL_ESTATE_FUNDS", "Fundos imobiliários"),
              opt("STOCKS", "Ações"),
              opt("CRYPTO", "Criptomoedas")),
          choice(
              MAIN_GOAL,
              "Qual o seu principal objetivo financeiro?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("PAY_DEBTS", "Quitar dívidas"),
              opt("EMERGENCY_FUND", "Montar uma reserva"),
              opt("BUY_ASSET", "Comprar um bem"),
              opt("RETIREMENT", "Aposentadoria")),
          choice(
              MARKET_DROP_REACTION,
              "Se um investimento seu caísse 20%, o que você faria?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("SELL_ALL", "Venderia tudo"),
              opt("WAIT", "Esperaria se recuperar"),
              opt("BUY_MORE", "Compraria mais")),
          choice(
              RISKY_HABITS,
              "Você gasta com algum destes?",
              Type.MULTIPLE_CHOICE,
              false,
              null,
              opt("BETS", "Apostas"),
              opt("ALCOHOL", "Bebida alcoólica"),
              opt("TOBACCO", "Cigarro")));

  private TriageQuestionnaire() {}

  public static List<TriageQuestionResponse> questions() {
    return QUESTIONS;
  }

  private static Option opt(String value, String label) {
    return new Option(value, label);
  }

  private static TriageQuestionResponse choice(
      String id,
      String text,
      Type type,
      boolean required,
      RequiredWhen requiredWhen,
      Option... options) {
    return new TriageQuestionResponse(
        id, text, type, required, List.of(options), null, null, null, requiredWhen);
  }

  private static TriageQuestionResponse number(
      String id, String text, BigDecimal max, int decimals, RequiredWhen requiredWhen) {
    return new TriageQuestionResponse(
        id, text, Type.NUMBER, true, null, BigDecimal.ZERO, max, decimals, requiredWhen);
  }

  private static TriageQuestionResponse bool(String id, String text, RequiredWhen requiredWhen) {
    return new TriageQuestionResponse(
        id, text, Type.BOOLEAN, true, null, null, null, null, requiredWhen);
  }
}
