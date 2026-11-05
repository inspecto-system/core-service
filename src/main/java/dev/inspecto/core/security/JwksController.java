package dev.inspecto.core.security;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publishes the public signing key so that other services verify tokens locally (ADR-007). */
@RestController
public class JwksController {

  private final JwtKeyProvider keys;

  /** Creates the controller. */
  public JwksController(JwtKeyProvider keys) {
    this.keys = keys;
  }

  /** JSON Web Key Set with public keys only. */
  @GetMapping("/api/v1/.well-known/jwks.json")
  public Map<String, Object> jwks() {
    return keys.publicJwks();
  }
}
