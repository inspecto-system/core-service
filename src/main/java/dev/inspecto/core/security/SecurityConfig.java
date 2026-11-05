package dev.inspecto.core.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Stateless bearer-token security of the API (ADR-007, ADR-009, SECURITY.md §2–3). */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({SecurityProperties.class, ServiceClientProperties.class})
public class SecurityConfig {

  /** Public endpoints of the API; everything else requires a valid access token. */
  private static final String[] PUBLIC_POST = {
    "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh"
  };

  /** Service-to-service endpoints; not routed by nginx (ADR-013). */
  private static final String INTERNAL = "/internal/**";

  /**
   * {@code /internal/**} accepts only service tokens ({@code aud=inspecto-internal}); user access
   * tokens fail the audience check here, and service tokens fail it on the public API (SECURITY.md
   * §8).
   */
  @Bean
  @Order(1)
  @SuppressWarnings(
      "PMD.SignatureDeclareThrowsException") // HttpSecurity.build() declares Exception
  @SuppressFBWarnings(
      value = {"SPRING_CSRF_PROTECTION_DISABLED", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"},
      justification =
          "Service-to-service calls with bearer tokens and no cookies; HttpSecurity.build()"
              + " declares Exception")
  SecurityFilterChain internalSecurity(
      HttpSecurity http,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
      JwtKeyProvider keys,
      SecurityProperties security,
      ServiceClientProperties clients)
      throws Exception {
    ProblemSecurityHandler problems = new ProblemSecurityHandler(resolver);
    JwtDecoder serviceTokens = decoder(keys, security.issuer(), clients.audience());
    http.securityMatcher(INTERNAL)
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(HttpMethod.POST, "/internal/v1/auth/service-token")
                .permitAll()
                .anyRequest()
                .authenticated())
        .oauth2ResourceServer(oauth ->
            oauth
                .jwt(jwt -> jwt.decoder(serviceTokens))
                .authenticationEntryPoint(problems)
                .accessDeniedHandler(problems))
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(problems).accessDeniedHandler(problems));
    return http.build();
  }

  @Bean
  @Order(2)
  @SuppressWarnings(
      "PMD.SignatureDeclareThrowsException") // HttpSecurity.build() declares Exception
  @SuppressFBWarnings(
      value = {"SPRING_CSRF_PROTECTION_DISABLED", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"},
      justification =
          "Stateless bearer-token API: no session or auth cookies, the SameSite=Strict refresh"
              + " cookie requires X-CSRF-Protection (SECURITY.md §4); HttpSecurity.build()"
              + " declares Exception")
  SecurityFilterChain apiSecurity(
      HttpSecurity http, @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver)
      throws Exception {
    ProblemSecurityHandler problems = new ProblemSecurityHandler(resolver);
    http
        // Stateless API: tokens travel in the Authorization header, not in cookies. The only
        // cookie (refresh token) is SameSite=Strict and its endpoint requires a custom header.
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(HttpMethod.POST, PUBLIC_POST)
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/.well-known/jwks.json")
                .permitAll()
                .requestMatchers("/health", "/health/**", "/error")
                .permitAll()
                .anyRequest()
                .authenticated())
        .oauth2ResourceServer(oauth ->
            oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter()))
                .authenticationEntryPoint(problems)
                .accessDeniedHandler(problems))
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(problems).accessDeniedHandler(problems));
    return http.build();
  }

  @Bean
  JwtEncoder jwtEncoder(JwtKeyProvider keys) {
    return new NimbusJwtEncoder(
        new ImmutableJWKSet<SecurityContext>(new JWKSet(keys.signingKey())));
  }

  /** Decoder of user access tokens, used by the public API. */
  @Bean
  JwtDecoder jwtDecoder(JwtKeyProvider keys, SecurityProperties properties) {
    return decoder(keys, properties.issuer(), properties.audience());
  }

  /** RS256-only decoder that checks the issuer, the lifetime and the given audience. */
  static JwtDecoder decoder(JwtKeyProvider keys, String issuer, String expectedAudience) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey(keys.publicKey())
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();
    OAuth2TokenValidator<Jwt> audience =
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.contains(expectedAudience));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), audience));
    return decoder;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  private static JwtAuthenticationConverter authenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("role");
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }
}
