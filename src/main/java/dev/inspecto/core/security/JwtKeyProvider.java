package dev.inspecto.core.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Holds the RSA key pair that signs access tokens (ADR-007). */
@Component
public final class JwtKeyProvider {

  private static final Logger LOG = LoggerFactory.getLogger(JwtKeyProvider.class);
  private static final int GENERATED_KEY_SIZE = 2048;

  private final RSAKey rsaKey;
  private final RSAPublicKey publicKey;

  /**
   * Loads the configured key or, when none is configured, generates an ephemeral one.
   *
   * <p>A configured key keeps the configured key id ({@code CORE_JWT_KEY_ID}), which changes on
   * rotation. A generated key is new at every start, so its id is its RFC 7638 thumbprint: services
   * that verify tokens with the JWKS (media-service) see an unknown {@code kid} after a restart and
   * reload the keys instead of rejecting every new token.
   */
  public JwtKeyProvider(SecurityProperties properties) throws GeneralSecurityException {
    String pem = properties.privateKey();
    boolean generated = pem == null || pem.isBlank();
    KeyPair keyPair = generated ? generate() : parse(pem);
    this.publicKey = (RSAPublicKey) keyPair.getPublic();
    RSAKey.Builder builder =
        new RSAKey.Builder(publicKey)
            .privateKey(keyPair.getPrivate())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256);
    this.rsaKey = generated ? withThumbprintId(builder) : builder.keyID(properties.keyId()).build();
  }

  private static RSAKey withThumbprintId(RSAKey.Builder builder) throws GeneralSecurityException {
    try {
      return builder.keyIDFromThumbprint().build();
    } catch (JOSEException e) {
      throw new GeneralSecurityException("Cannot compute the key thumbprint", e);
    }
  }

  /** Key pair including the private key, for signing. */
  public RSAKey signingKey() {
    return rsaKey;
  }

  /** Public key, for verification. */
  public RSAPublicKey publicKey() {
    return publicKey;
  }

  /** Public JSON Web Key Set without private parts. */
  public Map<String, Object> publicJwks() {
    return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
  }

  private static KeyPair generate() throws GeneralSecurityException {
    LOG.warn("No JWT private key configured: using an ephemeral key (tokens die with restart)");
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(GENERATED_KEY_SIZE);
    return generator.generateKeyPair();
  }

  private static KeyPair parse(String pem) throws GeneralSecurityException {
    String base64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
    byte[] der = Base64.getDecoder().decode(base64);
    KeyFactory factory = KeyFactory.getInstance("RSA");
    RSAPrivateCrtKey privateKey =
        (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(der));
    RSAPublicKeySpec publicSpec =
        new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
    return new KeyPair(factory.generatePublic(publicSpec), privateKey);
  }
}
