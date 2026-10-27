package br.com.finup.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Classifica categorias e descrições de despesas fora da lista de regras determinísticas utilizando
 * IA (Amazon Bedrock), com cache em memória thread-safe dos resultados para evitar chamadas
 * redundantes.
 *
 * <p>As despesas que ainda não estão no cache vão juntas para o Bedrock, em lotes de até {@value
 * #MAX_BATCH_SIZE}: o custo de uma requisição não cresce com o número de transações do usuário.
 *
 * <p>Em caso de falha da IA (timeout, indisponibilidade ou erro de parsing), degrada suavemente
 * tratando o lote como não arriscado sem armazenar no cache, assegurando que o endpoint nunca caia.
 */
@Component
public class AiSpendingClassifier {

  private static final Logger log = LoggerFactory.getLogger(AiSpendingClassifier.class);

  /**
   * Despesas distintas por chamada ao Bedrock; a resposta são só os números, cabe no max-tokens.
   */
  static final int MAX_BATCH_SIZE = 50;

  private static final String SYSTEM_PROMPT =
      """
      Você é um especialista em análise financeira do app FinUp.
      Sua tarefa é classificar, numa lista numerada de despesas (categoria e descrição), quais representam um gasto de risco prejudicial ou vício.

      SÃO gastos de risco:
      - Apostas esportivas, jogos de azar, rifas, bingos, loterias e cassinos;
      - Bebidas alcoólicas, bares, cervejarias, adegas e baladas focadas em consumo de álcool;
      - Drogas ilícitas e entorpecentes;
      - Cigarro, tabaco, fumo, charutos, narguilés, vapes, pods e cigarros eletrônicos;
      - Gastos compulsivos ou predatórios (como apostas online, cassino virtual, loot boxes e roletas de skins).

      NÃO são gastos de risco:
      - Alimentação, refeições, supermercados, feiras, padarias;
      - Farmácia, saúde, consultas médicas, dentista, exames e medicamentos;
      - Educação, faculdade, cursos, livros e material escolar;
      - Moradia, aluguel, condomínio, contas de água, luz, gás e internet;
      - Transporte, ônibus, metrô, táxi, aplicativo de corrida, combustível e manutenção de veículo;
      - Lazer saudável e consumo cotidiano (cinema, streaming, parques, teatro, roupas normais).

      Responda ESTRITAMENTE em formato JSON, com os números das despesas que são gasto de risco:
      {"risky": [1, 3]} ou, se nenhuma for, {"risky": []}
      """;

  private final BedrockLlmService llmService;
  private final ObjectMapper objectMapper;
  private final ConcurrentMap<ClassificationKey, Boolean> cache = new ConcurrentHashMap<>();

  public AiSpendingClassifier(BedrockLlmService llmService, ObjectMapper objectMapper) {
    this.llmService = llmService;
    this.objectMapper = objectMapper;
  }

  /**
   * Avalia quais despesas são de risco. Consulta primeiro o cache em memória; o que não estiver
   * nele vai para o Bedrock em lotes, e o resultado é armazenado.
   *
   * @param expenses despesas a classificar (repetidas contam uma vez)
   * @return as despesas consideradas de risco; se a IA falhar, as ainda não classificadas ficam de
   *     fora e não entram no cache
   */
  public Set<ClassificationKey> findRisky(Collection<ClassificationKey> expenses) {
    Set<ClassificationKey> risky = new HashSet<>();
    List<ClassificationKey> pending = new ArrayList<>();
    for (ClassificationKey key : new LinkedHashSet<>(expenses)) {
      Boolean cached = cache.get(key);
      if (cached == null) {
        pending.add(key);
      } else if (cached) {
        risky.add(key);
      }
    }
    log.debug("Classificacao de risco: {} no cache, {} para a IA", risky.size(), pending.size());

    for (int start = 0; start < pending.size(); start += MAX_BATCH_SIZE) {
      List<ClassificationKey> batch =
          pending.subList(start, Math.min(start + MAX_BATCH_SIZE, pending.size()));
      Optional<Set<Integer>> riskyNumbers = classify(batch);
      if (riskyNumbers.isEmpty()) {
        // Se a IA falhou neste lote, os próximos provavelmente falham também.
        break;
      }
      for (int i = 0; i < batch.size(); i++) {
        boolean isRisky = riskyNumbers.get().contains(i + 1);
        cache.put(batch.get(i), isRisky);
        if (isRisky) {
          risky.add(batch.get(i));
        }
      }
    }
    return risky;
  }

  public boolean isCached(String categoryName, String description) {
    return cache.containsKey(new ClassificationKey(categoryName, description));
  }

  public void clearCache() {
    cache.clear();
  }

  public int cacheSize() {
    return cache.size();
  }

  /** Números (a partir de 1) das despesas de risco no lote, ou vazio se a IA falhar. */
  private Optional<Set<Integer>> classify(List<ClassificationKey> batch) {
    try {
      String response = llmService.invoke(SYSTEM_PROMPT, buildUserMessage(batch), null);
      Optional<Set<Integer>> parsed = parseResponse(response);
      if (parsed.isEmpty()) {
        log.warn(
            "Resposta da IA fora do formato ao classificar {} despesas de risco", batch.size());
      }
      return parsed;
    } catch (Exception e) {
      log.warn(
          "Falha ao consultar IA para classificar {} despesas de risco: {}",
          batch.size(),
          e.getMessage());
      return Optional.empty();
    }
  }

  private String buildUserMessage(List<ClassificationKey> batch) {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < batch.size(); i++) {
      ClassificationKey key = batch.get(i);
      String cat = key.category().isEmpty() ? "não informada" : key.category();
      String desc = key.description().isEmpty() ? "nenhuma informada" : key.description();
      text.append("%d. Categoria: %s | Descrição: %s\n".formatted(i + 1, cat, desc));
    }
    return text.toString();
  }

  private Optional<Set<Integer>> parseResponse(String response) {
    if (response == null || response.isBlank()) {
      return Optional.empty();
    }

    String cleaned = response.trim();
    int start = cleaned.indexOf('{');
    int end = cleaned.lastIndexOf('}');
    if (start < 0 || end <= start) {
      return Optional.empty();
    }

    try {
      JsonNode risky = objectMapper.readTree(cleaned.substring(start, end + 1)).get("risky");
      if (risky == null || !risky.isArray()) {
        return Optional.empty();
      }
      Set<Integer> numbers = new HashSet<>();
      risky.forEach(number -> numbers.add(number.asInt()));
      return Optional.of(numbers);
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  /**
   * Despesa normalizada (sem acentos, minúscula, espaços e quebras de linha colapsados): é a chave
   * do cache e o texto que vai para a IA, uma linha por despesa.
   */
  public record ClassificationKey(String category, String description) {
    public ClassificationKey {
      category = normalize(category);
      description = normalize(description);
    }

    private static String normalize(String value) {
      if (value == null) {
        return "";
      }
      return Normalizer.normalize(value, Normalizer.Form.NFD)
          .replaceAll("\\p{M}", "")
          .replaceAll("\\s+", " ")
          .strip()
          .toLowerCase(Locale.ROOT);
    }
  }
}
