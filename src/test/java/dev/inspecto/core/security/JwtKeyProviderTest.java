package dev.inspecto.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JwtKeyProviderTest {

  @Test
  void shouldLoadConfiguredPkcs8Key() throws GeneralSecurityException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    String pem = pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());

    JwtKeyProvider provider = new JwtKeyProvider(properties(pem));

    assertThat(provider.publicKey()).isEqualTo(keyPair.getPublic());
    assertThat(provider.signingKey().getKeyID()).isEqualTo("test-key");
  }

  @Test
  void shouldGenerateEphemeralKeyWhenNoneConfigured() throws GeneralSecurityException {
    JwtKeyProvider provider = new JwtKeyProvider(properties(" "));

    assertThat(provider.publicKey().getModulus().bitLength()).isEqualTo(2048);
  }

  @Test
  void shouldPublishOnlyPublicParts() throws GeneralSecurityException {
    JwtKeyProvider provider = new JwtKeyProvider(properties(null));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> keys = (List<Map<String, Object>>) provider.publicJwks().get("keys");

    assertThat(keys).hasSize(1);
    assertThat(keys.get(0)).containsKeys("n", "e", "kid").doesNotContainKeys("d", "p", "q");
  }

  /**
   * Encodes a key generated at runtime as PEM. The armour is assembled from parts so the source
   * contains no key-shaped literal for secret scanners (Gitleaks) to report.
   */
  private static String pem(String label, byte[] der) {
    String dashes = "-".repeat(5);
    String body = Base64.getMimeEncoder().encodeToString(der);
    return dashes + "BEGIN " + label + dashes + "\n" + body + "\n" + dashes + "END " + label
        + dashes;
  }

  private static SecurityProperties properties(String privateKey) {
    return new SecurityProperties(
        "issuer",
        "audience",
        Duration.ofMinutes(15),
        Duration.ofDays(30),
        "test-key",
        privateKey,
        5,
        Duration.ofMinutes(15),
        true);
  }
}
