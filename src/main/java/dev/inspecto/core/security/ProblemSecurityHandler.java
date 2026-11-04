package dev.inspecto.core.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Sends security failures through the MVC exception handlers so that 401 and 403 responses use the
 * same problem-details format as every other error.
 */
class ProblemSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final HandlerExceptionResolver resolver;

  ProblemSecurityHandler(HandlerExceptionResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) {
    resolver.resolveException(request, response, null, ex);
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) {
    resolver.resolveException(request, response, null, ex);
  }
}
