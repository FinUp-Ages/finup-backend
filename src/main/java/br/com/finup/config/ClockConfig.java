package br.com.finup.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relogio da aplicacao, no fuso do usuario. "Hoje" precisa ser o dia de Brasilia mesmo com o
 * servidor em UTC — senao "gastei 7 reais hoje" a noite cairia no dia seguinte.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.system(ZoneId.of("America/Sao_Paulo"));
  }
}
