package dev.inspecto.core.suggestion;

import dev.inspecto.core.security.OwnServiceToken;
import dev.inspecto.core.security.ServiceTokenService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Internal API of ml-service (platform/api/ml.yaml 0.5.0). Calls carry a service token that
 * core-service signs itself with scope {@value #SCOPE}. Suggestions are optional: any failure is
 * logged and reported as "no answer", never as an error of the user's request (T-604).
 */
@Component
@EnableConfigurationProperties(MlProperties.class)
public class MlClient {

  /** Scope required by ml-service. */
  public static final String SCOPE = "ml.classify";

  private static final Logger LOG = LoggerFactory.getLogger(MlClient.class);

  private final RestClient http;
  private final OwnServiceToken token;

  /** Creates the client. */
  public MlClient(ServiceTokenService tokens, MlProperties properties, Clock clock) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.timeout());
    factory.setReadTimeout(properties.timeout());
    this.http = RestClient.builder().baseUrl(properties.url()).requestFactory(factory).build();
    this.token = new OwnServiceToken(tokens, clock, SCOPE);
  }

  /**
   * Categories ml-service suggests for the photo, empty when it gave no answer.
   *
   * @param photoId a photo the caller has checked the user may read
   */
  public Optional<Classification> classify(UUID photoId) {
    try {
      return Optional.ofNullable(
          http.post()
              .uri("/internal/v1/classify")
              .header(HttpHeaders.AUTHORIZATION, token.bearer())
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("photoId", photoId))
              .retrieve()
              .body(Classification.class));
    } catch (RestClientException e) {
      LOG.warn("ml-service gave no suggestion", e);
      return Optional.empty();
    }
  }

  /**
   * Answer of ml-service.
   *
   * @param modelVersion version of the model that answered
   * @param predictions up to three categories by probability, possibly empty
   */
  public record Classification(String modelVersion, List<Prediction> predictions) {

    /** Defensive copy; ml-service never sends {@code null}, but the type allows it. */
    public Classification {
      predictions = predictions == null ? List.of() : List.copyOf(predictions);
    }
  }

  /** A category code with its probability. */
  public record Prediction(String code, double probability) {}
}
