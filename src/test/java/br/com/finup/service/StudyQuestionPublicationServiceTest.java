package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.dto.StudyQuestionResponse;
import br.com.finup.exception.ConflictException;
import br.com.finup.exception.InvalidStudyQuestionException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.QuestionStatus;
import br.com.finup.model.QuestionType;
import br.com.finup.model.StudyCategory;
import br.com.finup.model.StudyQuestion;
import br.com.finup.repository.StudyQuestionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@ExtendWith(MockitoExtension.class)
class StudyQuestionPublicationServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-26T15:00:00Z");
  private static final Instant EARLIER = Instant.parse("2026-10-01T12:00:00Z");

  private static final String FILL_BLANK =
      "{\"type\":\"FILL_BLANK\",\"title\":\"CDB\",\"statement\":\"O CDB rende ___ do CDI\","
          + "\"answers\":[\"100%\"],\"explanation\":\"Rendimento.\"}";
  private static final String MULTIPLE_CHOICE =
      "{\"type\":\"MULTIPLE_CHOICE\",\"statement\":\"Qual opção?\",\"options\":"
          + "[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false}]}";
  private static final String LEARNING =
      "{\"type\":\"LEARNING\",\"title\":\"Reserva\",\"body\":\"## Reserva de emergência\"}";
  private static final String LEARNING_EDITED =
      "{\"type\":\"LEARNING\",\"title\":\"Reserva\",\"body\":\"## Texto novo\"}";

  /** Como o do Spring Boot: campo desconhecido no JSON gravado nao quebra a conversao. */
  private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

  private final StudyCategory category = new StudyCategory("Reserva", "Conceitos");

  @Mock private EntityManager entityManager;
  @Mock private StudyQuestionRepository questionRepository;

  private StudyQuestionPublicationService service;
  private final UUID id = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new StudyQuestionPublicationService(
            entityManager,
            questionRepository,
            new StudyQuestionValidator(),
            objectMapper,
            Clock.fixed(NOW, ZoneOffset.UTC));
    lenient()
        .when(questionRepository.saveAndFlush(any(StudyQuestion.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private Map<String, Object> content(String json) throws Exception {
    return objectMapper.readValue(json, new TypeReference<>() {});
  }

  /** Cria a pergunta e a deixa disponível para o lock pessimista, que é como o service a busca. */
  private StudyQuestion existing(QuestionType type, String draft, String published, Instant at)
      throws Exception {
    StudyQuestion question =
        new StudyQuestion(
            category, type, content(draft), published == null ? null : content(published), at);
    when(entityManager.find(StudyQuestion.class, id, LockModeType.PESSIMISTIC_WRITE))
        .thenReturn(question);
    return question;
  }

  // ---------------------------------------------------------------- publish

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "FILL_BLANK|" + FILL_BLANK,
        "MULTIPLE_CHOICE|" + MULTIPLE_CHOICE,
        "LEARNING|" + LEARNING
      })
  @DisplayName(
      "publica o rascunho valido de cada tipo: published igual ao draft e publishedAt agora")
  void publishesValidDraftOfEveryType(QuestionType type, String json) throws Exception {
    StudyQuestion question = existing(type, json, null, null);

    StudyQuestionResponse response = service.publish(id);

    assertThat(response.status()).isEqualTo(QuestionStatus.PUBLISHED);
    assertThat(response.published()).isEqualTo(content(json)).isEqualTo(response.draft());
    assertThat(response.publishedAt()).isEqualTo(NOW);
    assertThat(question.getPublished()).isEqualTo(question.getDraft());
    verify(questionRepository).saveAndFlush(question);
  }

  @Test
  @DisplayName("publishedAt e truncado para microssegundos, a precisao que o banco guarda")
  void publishedAtIsTruncatedToTheColumnPrecision() throws Exception {
    Instant withNanos = Instant.parse("2026-10-26T15:00:00.123456789Z");
    StudyQuestionPublicationService nanoClockService =
        new StudyQuestionPublicationService(
            entityManager,
            questionRepository,
            new StudyQuestionValidator(),
            objectMapper,
            Clock.fixed(withNanos, ZoneOffset.UTC));
    existing(QuestionType.LEARNING, LEARNING, null, null);

    StudyQuestionResponse response = nanoClockService.publish(id);

    assertThat(response.publishedAt()).isEqualTo(Instant.parse("2026-10-26T15:00:00.123456Z"));
  }

  @Test
  @DisplayName("republica uma pergunta com edicao pendente: published passa a ser o draft novo")
  void publishesPendingChanges() throws Exception {
    StudyQuestion question = existing(QuestionType.LEARNING, LEARNING_EDITED, LEARNING, EARLIER);
    assertThat(question.getStatus()).isEqualTo(QuestionStatus.PUBLISHED_WITH_CHANGES);

    StudyQuestionResponse response = service.publish(id);

    assertThat(response.status()).isEqualTo(QuestionStatus.PUBLISHED);
    assertThat(response.published()).isEqualTo(content(LEARNING_EDITED));
    assertThat(response.publishedAt()).isEqualTo(NOW).isAfter(EARLIER);
  }

  @Test
  @DisplayName("publicar o que ja esta publicado e sem edicao nao grava nada e mantem publishedAt")
  void publishingAnUnchangedPublishedQuestionIsANoOp() throws Exception {
    // O conteudo e invalido de proposito: sem edicao pendente nao ha o que validar nem publicar.
    String legacy = "{\"type\":\"LEARNING\",\"content\":\"formato antigo\"}";
    existing(QuestionType.LEARNING, legacy, legacy, EARLIER);

    StudyQuestionResponse response = service.publish(id);

    assertThat(response.status()).isEqualTo(QuestionStatus.PUBLISHED);
    assertThat(response.publishedAt()).isEqualTo(EARLIER);
    verify(questionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("draft e published nao compartilham listas nem mapas depois de publicar")
  void publishedIsADeepCopyOfTheDraft() throws Exception {
    StudyQuestion question = existing(QuestionType.MULTIPLE_CHOICE, MULTIPLE_CHOICE, null, null);

    service.publish(id);

    assertThat(question.getPublished()).isNotSameAs(question.getDraft());
    assertThat(question.getPublished().get("options"))
        .isNotSameAs(question.getDraft().get("options"));
    @SuppressWarnings("unchecked")
    List<Object> draftOptions = (List<Object>) question.getDraft().get("options");
    draftOptions.clear();
    assertThat(question.getPublished().get("options")).asList().hasSize(2);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "FILL_BLANK sem respostas|FILL_BLANK|{\"type\":\"FILL_BLANK\",\"title\":\"T\","
            + "\"statement\":\"S ___\",\"answers\":[]}",
        "FILL_BLANK com titulo em branco|FILL_BLANK|{\"type\":\"FILL_BLANK\",\"title\":\" \","
            + "\"statement\":\"S ___\",\"answers\":[\"x\"]}",
        "FILL_BLANK com resposta vazia|FILL_BLANK|{\"type\":\"FILL_BLANK\",\"title\":\"T\","
            + "\"statement\":\"S ___\",\"answers\":[\"\"]}",
        "MULTIPLE_CHOICE sem correta|MULTIPLE_CHOICE|{\"type\":\"MULTIPLE_CHOICE\","
            + "\"statement\":\"S\",\"options\":[{\"text\":\"A\",\"correct\":false}]}",
        "MULTIPLE_CHOICE sem alternativas|MULTIPLE_CHOICE|{\"type\":\"MULTIPLE_CHOICE\","
            + "\"statement\":\"S\",\"options\":[]}",
        "MULTIPLE_CHOICE sem flag correct|MULTIPLE_CHOICE|{\"type\":\"MULTIPLE_CHOICE\","
            + "\"statement\":\"S\",\"options\":[{\"text\":\"A\"}]}",
        "LEARNING sem corpo|LEARNING|{\"type\":\"LEARNING\",\"title\":\"T\"}",
        "LEARNING sem titulo|LEARNING|{\"type\":\"LEARNING\",\"body\":\"B\"}",
        "draft sem type|LEARNING|{\"title\":\"T\",\"body\":\"B\"}",
        "formato antigo do seed|LEARNING|{\"type\":\"LEARNING\",\"content\":\"texto\"}"
      })
  @DisplayName("rascunho invalido nao publica nada e responde 400 (InvalidStudyQuestionException)")
  void invalidDraftIsNotPublished(String name, QuestionType type, String draft) throws Exception {
    StudyQuestion question = existing(type, draft, null, null);

    assertThatThrownBy(() -> service.publish(id))
        .isInstanceOf(InvalidStudyQuestionException.class)
        .hasMessageContaining("não foi publicado");

    assertThat(question.getPublished()).isNull();
    assertThat(question.getPublishedAt()).isNull();
    assertThat(question.getStatus()).isEqualTo(QuestionStatus.DRAFT);
    verify(questionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("rascunho com type desconhecido ou fora do formato nao publica")
  void draftWithUnknownTypeIsNotPublished() throws Exception {
    existing(
        QuestionType.LEARNING, "{\"type\":\"VIDEO\",\"title\":\"T\",\"body\":\"B\"}", null, null);

    assertThatThrownBy(() -> service.publish(id))
        .isInstanceOf(InvalidStudyQuestionException.class)
        .hasMessageContaining("formato inválido");
    verify(questionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("rascunho valido de outro tipo que o da pergunta nao publica")
  void draftOfADifferentTypeIsNotPublished() throws Exception {
    existing(QuestionType.FILL_BLANK, LEARNING, null, null);

    assertThatThrownBy(() -> service.publish(id))
        .isInstanceOf(InvalidStudyQuestionException.class)
        .hasMessageContaining("tipo do rascunho");
    verify(questionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("rascunho invalido com versao publicada mantem a publicada como esta")
  void invalidDraftKeepsThePublishedVersion() throws Exception {
    StudyQuestion question =
        existing(
            QuestionType.LEARNING, "{\"type\":\"LEARNING\",\"title\":\"T\"}", LEARNING, EARLIER);

    assertThatThrownBy(() -> service.publish(id)).isInstanceOf(InvalidStudyQuestionException.class);

    assertThat(question.getPublished()).isEqualTo(content(LEARNING));
    assertThat(question.getPublishedAt()).isEqualTo(EARLIER);
    assertThat(question.getStatus()).isEqualTo(QuestionStatus.PUBLISHED_WITH_CHANGES);
  }

  // -------------------------------------------------------------- unpublish

  @Test
  @DisplayName("despublica: zera published e publishedAt, e a pergunta volta a DRAFT")
  void unpublishReturnsToDraft() throws Exception {
    StudyQuestion question = existing(QuestionType.LEARNING, LEARNING, LEARNING, EARLIER);

    StudyQuestionResponse response = service.unpublish(id);

    assertThat(response.status()).isEqualTo(QuestionStatus.DRAFT);
    assertThat(response.published()).isNull();
    assertThat(response.publishedAt()).isNull();
    assertThat(question.getDraft()).isEqualTo(content(LEARNING));
    verify(questionRepository).saveAndFlush(question);
  }

  @Test
  @DisplayName("despublicar com edicao pendente preserva o rascunho editado")
  void unpublishKeepsPendingEdits() throws Exception {
    StudyQuestion question = existing(QuestionType.LEARNING, LEARNING_EDITED, LEARNING, EARLIER);

    StudyQuestionResponse response = service.unpublish(id);

    assertThat(response.status()).isEqualTo(QuestionStatus.DRAFT);
    assertThat(question.getDraft()).isEqualTo(content(LEARNING_EDITED));
  }

  @Test
  @DisplayName("despublicar o que nunca foi publicado nao grava nada")
  void unpublishOfADraftIsANoOp() throws Exception {
    existing(QuestionType.LEARNING, LEARNING, null, null);

    StudyQuestionResponse response = service.unpublish(id);

    assertThat(response.status()).isEqualTo(QuestionStatus.DRAFT);
    verify(questionRepository, never()).saveAndFlush(any());
  }

  // ----------------------------------------------------------- discardDraft

  @Test
  @DisplayName("descarta a edicao pendente: draft volta a ser o published e a pergunta a PUBLISHED")
  void discardRestoresDraftFromPublished() throws Exception {
    StudyQuestion question = existing(QuestionType.LEARNING, LEARNING_EDITED, LEARNING, EARLIER);

    StudyQuestionResponse response = service.discardDraft(id);

    assertThat(response.status()).isEqualTo(QuestionStatus.PUBLISHED);
    assertThat(response.draft()).isEqualTo(content(LEARNING));
    assertThat(response.published()).isEqualTo(content(LEARNING));
    assertThat(response.publishedAt()).isEqualTo(EARLIER);
    assertThat(question.getDraft()).isNotSameAs(question.getPublished());
    verify(questionRepository).saveAndFlush(question);
  }

  @Test
  @DisplayName("descartar sem versao publicada responde 409 e nao altera o rascunho")
  void discardWithoutPublishedVersionIsAConflict() throws Exception {
    StudyQuestion question = existing(QuestionType.LEARNING, LEARNING_EDITED, null, null);

    assertThatThrownBy(() -> service.discardDraft(id))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("não tem versão publicada");

    assertThat(question.getDraft()).isEqualTo(content(LEARNING_EDITED));
    verify(questionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("descartar sem edicao pendente nao grava nada")
  void discardWithoutPendingChangesIsANoOp() throws Exception {
    existing(QuestionType.LEARNING, LEARNING, LEARNING, EARLIER);

    StudyQuestionResponse response = service.discardDraft(id);

    assertThat(response.status()).isEqualTo(QuestionStatus.PUBLISHED);
    verify(questionRepository, never()).saveAndFlush(any());
  }

  // -------------------------------------------------------------- erros comuns

  @ParameterizedTest
  @ValueSource(strings = {"publish", "unpublish", "discardDraft"})
  @DisplayName("pergunta inexistente responde 404 nas tres acoes")
  void unknownQuestionIsNotFound(String action) {
    when(entityManager.find(StudyQuestion.class, id, LockModeType.PESSIMISTIC_WRITE))
        .thenReturn(null);

    assertThatThrownBy(
            () -> {
              switch (action) {
                case "publish" -> service.publish(id);
                case "unpublish" -> service.unpublish(id);
                default -> service.discardDraft(id);
              }
            })
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Pergunta da trilha não encontrada");
  }
}
