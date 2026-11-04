package dev.inspecto.core.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Accepts or generates {@code X-Request-Id}, puts it into the log context and the response. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  /** Header that carries the correlation identifier. */
  public static final String HEADER = "X-Request-Id";

  private static final String MDC_KEY = "requestId";
  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{1,64}");

  /** Returns the identifier of the request processed by the current thread. */
  public static String current() {
    return MDC.get(MDC_KEY);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String incoming = request.getHeader(HEADER);
    boolean valid = incoming != null && VALID.matcher(incoming).matches();
    String requestId = valid ? incoming : UUID.randomUUID().toString();
    MDC.put(MDC_KEY, requestId);
    response.setHeader(HEADER, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }
}
