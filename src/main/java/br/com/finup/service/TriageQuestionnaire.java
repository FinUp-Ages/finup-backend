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
  public static final String HOUSING_TYPE = "HOUSING_TYPE";
  public static final String EXTRA_INCOME = "EXTRA_INCOME";
  public static final String MONTHLY_SAVINGS = "MONTHLY_SAVINGS";
  public static final String BUDGET_TRACKING = "BUDGET_TRACKING";
  public static final String USES_CREDIT_CARD = "USES_CREDIT_CARD";
  public static final String CARD_BILL_PAYMENT = "CARD_BILL_PAYMENT";
  public static final String HAS_INSURANCE = "HAS_INSURANCE";
  public static final String GOAL_HORIZON = "GOAL_HORIZON";
  public static final String FINANCIAL_KNOWLEDGE = "FINANCIAL_KNOWLEDGE";

  private static final BigDecimal MAX_COUNT = BigDecimal.valueOf(20);
  private static final BigDecimal MAX_MONEY = BigDecimal.valueOf(100_000_000);

  private static final RequiredWhen WHEN_HAS_DEBT = new RequiredWhen(HAS_DEBT, true);
  private static final RequiredWhen WHEN_INVESTS = new RequiredWhen(INVESTS, true);
  private static final RequiredWhen WHEN_USES_CARD = new RequiredWhen(USES_CREDIT_CARD, true);

  private static final List<TriageQuestionResponse> QUESTIONS =
      List.of(
          choice(
              INCOME_TYPE,
              "Como você classifica a sua renda?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("FIXED", "Fixa (salário ou benefício regular)"),
              opt("VARIABLE", "Variável (autônomo ou comissionado)"),
              opt("MIXED", "Mista (parte fixa e parte variável)")),
          number(CHILDREN_COUNT, "Quantos filhos você possui?", MAX_COUNT, 0, null),
          number(
              DEPENDENTS_COUNT,
              "Quantas outras pessoas dependem financeiramente de você?",
              MAX_COUNT,
              0,
              null),
          choice(
              HOUSING_TYPE,
              "Qual é a sua situação de moradia?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("OWNED", "Imóvel próprio quitado"),
              opt("FINANCED", "Imóvel próprio financiado"),
              opt("RENTED", "Imóvel alugado"),
              opt("PROVIDED", "Imóvel cedido ou moro com familiares")),
          bool(EXTRA_INCOME, "Você possui alguma fonte de renda além da principal?", null),
          choice(
              INCOME_EXPENSE_RELATION,
              "Em geral, os seus gastos são menores, iguais ou maiores do que a sua renda?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("SPENDS_LESS", "Menores do que a minha renda"),
              opt("BALANCED", "Iguais à minha renda"),
              opt("SPENDS_MORE", "Maiores do que a minha renda")),
          number(
              MONTHLY_EXPENSES, "Qual é, em média, o seu gasto mensal total?", MAX_MONEY, 2, null),
          number(
              MONTHLY_SAVINGS,
              "Quanto você consegue poupar, em média, por mês?",
              MAX_MONEY,
              2,
              null),
          choice(
              SPENDING_HABIT,
              "Como você descreveria o seu comportamento de consumo?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("CONTROLLED", "Controlado: planejo antes de gastar"),
              opt("MODERATE", "Moderado: planejo parte dos gastos"),
              opt("IMPULSIVE", "Impulsivo: costumo comprar sem planejar")),
          choice(
              BUDGET_TRACKING,
              "Com que frequência você acompanha as suas despesas?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("ALWAYS", "Sempre"),
              opt("SOMETIMES", "Às vezes"),
              opt("NEVER", "Nunca")),
          bool(HAS_DEBT, "Você possui dívidas em aberto?", null),
          choice(
              DEBT_TYPES,
              "Quais tipos de dívida você possui?",
              Type.MULTIPLE_CHOICE,
              true,
              WHEN_HAS_DEBT,
              opt("CREDIT_CARD", "Cartão de crédito"),
              opt("OVERDRAFT", "Cheque especial"),
              opt("PERSONAL_LOAN", "Empréstimo pessoal"),
              opt("FINANCING", "Financiamento"),
              opt("OTHER", "Outra")),
          number(
              DEBT_TOTAL_AMOUNT,
              "Qual é o valor total das suas dívidas?",
              MAX_MONEY,
              2,
              WHEN_HAS_DEBT),
          bool(
              LATE_PAYMENTS_6M,
              "Nos últimos 6 meses, você deixou de pagar alguma conta no vencimento?",
              null),
          bool(USES_CREDIT_CARD, "Você utiliza cartão de crédito?", null),
          choice(
              CARD_BILL_PAYMENT,
              "Como você costuma pagar a fatura do cartão de crédito?",
              Type.SINGLE_CHOICE,
              true,
              WHEN_USES_CARD,
              opt("FULL", "Pago o valor integral"),
              opt("PARTIAL", "Pago parte do valor"),
              opt("MINIMUM", "Pago apenas o valor mínimo")),
          choice(
              EMERGENCY_RESERVE,
              "Quanto você possui de reserva de emergência?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("NONE", "Não possuo reserva"),
              opt("UP_TO_3_MONTHS", "Até 3 meses de despesas"),
              opt("FROM_3_TO_6_MONTHS", "De 3 a 6 meses de despesas"),
              opt("OVER_6_MONTHS", "Mais de 6 meses de despesas")),
          bool(
              HAS_INSURANCE,
              "Você possui algum tipo de seguro (saúde, vida ou patrimonial)?",
              null),
          bool(INVESTS, "Você realiza investimentos atualmente?", null),
          number(
              INVESTED_AMOUNT,
              "Qual é o valor total que você tem investido?",
              MAX_MONEY,
              2,
              WHEN_INVESTS),
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
              "Em quais tipos de investimento você tem interesse?",
              Type.MULTIPLE_CHOICE,
              false,
              null,
              opt("SAVINGS", "Poupança"),
              opt("CDB", "CDB"),
              opt("TREASURY", "Tesouro Direto"),
              opt("FUNDS", "Fundos de investimento"),
              opt("REAL_ESTATE_FUNDS", "Fundos imobiliários"),
              opt("STOCKS", "Ações"),
              opt("CRYPTO", "Criptomoedas")),
          choice(
              MAIN_GOAL,
              "Qual é o seu principal objetivo financeiro no momento?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("PAY_DEBTS", "Quitar dívidas"),
              opt("EMERGENCY_FUND", "Constituir uma reserva de emergência"),
              opt("BUY_ASSET", "Adquirir um bem"),
              opt("RETIREMENT", "Planejar a aposentadoria")),
          choice(
              GOAL_HORIZON,
              "Em quanto tempo você pretende alcançar esse objetivo?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("SHORT_TERM", "Em até 1 ano"),
              opt("MEDIUM_TERM", "De 1 a 5 anos"),
              opt("LONG_TERM", "Em mais de 5 anos")),
          choice(
              MARKET_DROP_REACTION,
              "Caso um investimento seu sofresse queda de 20%, qual seria a sua atitude?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("SELL_ALL", "Venderia todo o investimento"),
              opt("WAIT", "Aguardaria a recuperação"),
              opt("BUY_MORE", "Investiria mais")),
          choice(
              FINANCIAL_KNOWLEDGE,
              "Como você avalia o seu conhecimento sobre finanças?",
              Type.SINGLE_CHOICE,
              true,
              null,
              opt("BEGINNER", "Iniciante"),
              opt("INTERMEDIATE", "Intermediário"),
              opt("ADVANCED", "Avançado")),
          choice(
              RISKY_HABITS,
              "Você possui gastos recorrentes com algum destes itens?",
              Type.MULTIPLE_CHOICE,
              false,
              null,
              opt("BETS", "Apostas"),
              opt("ALCOHOL", "Bebidas alcoólicas"),
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
