package br.com.finup.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS para o painel web (Vite) e para o app mobile em desenvolvimento.
 *
 * <p>As origens vem de {@code CORS_ALLOWED_ORIGINS}, separadas por virgula — nunca fixe host de
 * producao em codigo. O default cobre o Vite local. Nao use {@code *}: com credenciais habilitadas
 * o navegador rejeita o curinga.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  private final String[] allowedOrigins;

  public CorsConfig(
      @Value("${finup.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
