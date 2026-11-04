package br.com.finup.service;

import br.com.finup.dto.FinancialProfileQuestionResponse;
import br.com.finup.mapper.FinancialProfileQuestionMapper;
import br.com.finup.repository.FinancialProfileQuestionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura das perguntas de perfil financeiro do onboarding, para o app montar o questionario.
 *
 * <p>So leitura: o conteudo e gravado direto no banco (ver {@code database/init/02-required-data.
 * sql}), sem endpoint de escrita ainda.
 */
@Service
public class FinancialProfileQuestionService {

  private final FinancialProfileQuestionRepository questionRepository;

  public FinancialProfileQuestionService(FinancialProfileQuestionRepository questionRepository) {
    this.questionRepository = questionRepository;
  }

  @Transactional(readOnly = true)
  public List<FinancialProfileQuestionResponse> findAll() {
    return FinancialProfileQuestionMapper.toResponses(
        questionRepository.findAllWithOptionsOrderByOrderIndex());
  }
}
