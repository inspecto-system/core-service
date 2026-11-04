package dev.inspecto.core.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

  /** Public endpoints of the API; everything else requires a valid access token. */
  private static final String[] PUBLIC_POST = {
    "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh"
  };

  @Bean
  @SuppressWarnings(
      "PMD.SignatureDeclareThrowsException") // HttpSecurity.build() declares Exception
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

  @Bean
  JwtDecoder jwtDecoder(JwtKeyProvider keys, SecurityProperties properties) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey(keys.publicKey())
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();
    OAuth2TokenValidator<Jwt> audience =
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.contains(properties.audience()));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.issuer()), audience));
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
