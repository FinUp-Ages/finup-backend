package br.com.finup.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.Locale;
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
 * <p>Em caso de falha da IA (timeout, indisponibilidade ou erro de parsing), degrada suavemente
 * retornando {@code false} sem armazenar no cache, assegurando que o endpoint nunca caia.
 */
@Component
public class AiSpendingClassifier {

  private static final Logger log = LoggerFactory.getLogger(AiSpendingClassifier.class);

  private static final String SYSTEM_PROMPT =
      """
      Você é um especialista em análise financeira do app FinUp.
      Sua tarefa é classificar se uma despesa informada (categoria e descrição) representa um gasto de risco prejudicial ou vício.

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

      Responda ESTRITAMENTE em formato JSON:
      {"isRisky": true} ou {"isRisky": false}
      """;

  private final BedrockLlmService llmService;
  private final ObjectMapper objectMapper;
  private final ConcurrentMap<ClassificationKey, Boolean> cache = new ConcurrentHashMap<>();

  public AiSpendingClassifier(BedrockLlmService llmService, ObjectMapper objectMapper) {
    this.llmService = llmService;
    this.objectMapper = objectMapper;
  }

  /**
   * Avalia se o gasto é de risco. Consulta primeiro o cache em memória; se não encontrar, chama o
   * Bedrock e armazena o resultado.
   *
   * @param categoryName nome da categoria
   * @param description descrição livre da transação
   * @return {@code true} se considerado gasto de risco, {@code false} caso contrário ou em caso de
   *     erro
   */
  public boolean isRisky(String categoryName, String description) {
    ClassificationKey key = new ClassificationKey(categoryName, description);
    Boolean cached = cache.get(key);
    if (cached != null) {
      log.debug("Cache hit para classificacao de risco: {}", key);
      return cached;
    }

    try {
      String userMessage = buildUserMessage(categoryName, description);
      String response = llmService.invoke(SYSTEM_PROMPT, userMessage, null);
      boolean risky = parseResponse(response);
      cache.put(key, risky);
      log.debug("Classificacao da IA armazenada no cache: {} -> {}", key, risky);
      return risky;
    } catch (Exception e) {
      log.warn(
          "Falha ao consultar IA para classificar gasto de risco (categoria='{}', descricao='{}'): {}",
          categoryName,
          description,
          e.getMessage());
      return false;
    }
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

  private String buildUserMessage(String categoryName, String description) {
    String desc =
        (description == null || description.isBlank()) ? "nenhuma informada" : description.strip();
    String cat =
        (categoryName == null || categoryName.isBlank()) ? "não informada" : categoryName.strip();
    return "Categoria: %s\nDescrição: %s".formatted(cat, desc);
  }

  private boolean parseResponse(String response) {
    if (response == null || response.isBlank()) {
      return false;
    }

    String cleaned = response.trim();
    if (cleaned.startsWith("```")) {
      cleaned =
          cleaned.replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("(?s)\\s*```$", "").trim();
    }

    int start = cleaned.indexOf('{');
    int end = cleaned.lastIndexOf('}');
    if (start >= 0 && end > start) {
      cleaned = cleaned.substring(start, end + 1);
    }

    try {
      JsonNode node = objectMapper.readTree(cleaned);
      if (node.hasNonNull("isRisky")) {
        return node.get("isRisky").asBoolean(false);
      }
    } catch (Exception e) {
      log.warn(
          "Erro ao fazer parse do JSON retornado pela IA ('{}'): {}", response, e.getMessage());
    }

    return cleaned.toLowerCase(Locale.ROOT).contains("\"isrisky\": true")
        || cleaned.toLowerCase(Locale.ROOT).contains("\"isrisky\":true");
  }

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
          .strip()
          .toLowerCase(Locale.ROOT);
    }
  }
}
