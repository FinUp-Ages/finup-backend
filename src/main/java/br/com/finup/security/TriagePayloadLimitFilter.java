package br.com.finup.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limita o tamanho do corpo do envio da triagem. O questionário tem poucas respostas curtas, então
 * o teto é folgado para o uso real e baixo para quem tentar entupir o servidor.
 *
 * <p>Corpo que declara {@code Content-Length} acima do teto é recusado com 413 antes de ser lido.
 * Corpo sem tamanho declarado (chunked) ou que minta sobre ele é cortado ao passar do teto, e a
 * leitura falha.
 */
@Component
public class TriagePayloadLimitFilter extends OncePerRequestFilter {

  static final String PATH = "/api/v1/triage/answers";
  static final long MAX_BODY_BYTES = 16 * 1024;

  private static final URI TYPE_PAYLOAD_TOO_LARGE =
      URI.create("https://finup.com.br/errors/payload-too-large");

  private final ObjectMapper objectMapper;

  public TriagePayloadLimitFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !HttpMethod.POST.matches(request.getMethod())
        || !(request.getContextPath() + PATH).equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > MAX_BODY_BYTES) {
      SecurityProblemResponse.write(
          response,
          objectMapper,
          HttpStatus.PAYLOAD_TOO_LARGE,
          TYPE_PAYLOAD_TOO_LARGE,
          "O corpo da requisicao excede " + MAX_BODY_BYTES + " bytes.");
      return;
    }
    chain.doFilter(new LimitedRequest(request), response);
  }

  private static final class LimitedRequest extends HttpServletRequestWrapper {

    LimitedRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      return new LimitedStream(super.getInputStream());
    }
  }

  private static final class LimitedStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private long read;

    LimitedStream(ServletInputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public int read() throws IOException {
      int b = delegate.read();
      if (b != -1) {
        count(1);
      }
      return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int n = delegate.read(buffer, offset, length);
      if (n > 0) {
        count(n);
      }
      return n;
    }

    private void count(int n) throws IOException {
      read += n;
      if (read > MAX_BODY_BYTES) {
        throw new IOException("Corpo acima do limite de " + MAX_BODY_BYTES + " bytes");
      }
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
      delegate.setReadListener(listener);
    }
  }
}
