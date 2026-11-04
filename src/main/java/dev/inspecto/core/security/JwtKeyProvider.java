package dev.inspecto.core.security;

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
public class JwtKeyProvider {

  private static final Logger LOG = LoggerFactory.getLogger(JwtKeyProvider.class);
  private static final int GENERATED_KEY_SIZE = 2048;

  private final RSAKey rsaKey;
  private final RSAPublicKey publicKey;

  /** Loads the configured key or, when none is configured, generates an ephemeral one. */
  public JwtKeyProvider(SecurityProperties properties) throws GeneralSecurityException {
    String pem = properties.privateKey();
    KeyPair keyPair = pem == null || pem.isBlank() ? generate() : parse(pem);
    this.publicKey = (RSAPublicKey) keyPair.getPublic();
    this.rsaKey =
        new RSAKey.Builder(publicKey)
            .privateKey(keyPair.getPrivate())
            .keyID(properties.keyId())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build();
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
