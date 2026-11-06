package dev.inspecto.core.media;

import dev.inspecto.core.security.ServiceTokenService;
import dev.inspecto.core.security.ServiceTokenService.IssuedServiceToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Internal API of media-service (platform/api/media.yaml, tag internal). Calls carry a service
 * token that core-service signs itself with scope {@value #SCOPE}.
 */
@Component
@EnableConfigurationProperties(MediaProperties.class)
public class MediaClient {

  /** Scope required by the photo management endpoints of media-service. */
  public static final String SCOPE = "photos.manage";

  /** A token is renewed this long before it expires. */
  private static final Duration RENEW_MARGIN = Duration.ofSeconds(30);

  private static final String PHOTO = "/internal/v1/photos/{photoId}";

  private final RestClient http;
  private final ServiceTokenService tokens;
  private final MediaProperties properties;
  private final Clock clock;

  private final ReentrantLock tokenLock = new ReentrantLock();
  private String token;
  private Instant tokenRenewAt = Instant.MIN;

  /** Creates the client. */
  public MediaClient(ServiceTokenService tokens, MediaProperties properties, Clock clock) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.timeout());
    factory.setReadTimeout(properties.timeout());
    this.http = RestClient.builder().baseUrl(properties.url()).requestFactory(factory).build();
    this.tokens = tokens;
    this.properties = properties;
    this.clock = clock;
  }

  /** Photos allowed on one observation. */
  public int maxPhotosPerObservation() {
    return properties.maxPhotosPerObservation();
  }

  /**
   * Metadata of a photo, empty when media-service does not know it.
   *
   * @throws MediaUnavailableException when media-service cannot answer
   */
  public Optional<PhotoMetadata> metadata(UUID photoId) {
    try {
      return Optional.ofNullable(
          http.get()
              .uri(PHOTO, photoId)
              .header(HttpHeaders.AUTHORIZATION, bearer())
              .retrieve()
              .body(PhotoMetadata.class));
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        return Optional.empty();
      }
      throw unavailable(e);
    } catch (RestClientException e) {
      throw unavailable(e);
    }
  }

  /**
   * Marks a photo as attached; repeating it changes nothing.
   *
   * @return {@code false} when media-service does not know the photo
   * @throws MediaUnavailableException when media-service cannot answer
   */
  public boolean attach(UUID photoId) {
    try {
      http.post()
          .uri(PHOTO + "/attach", photoId)
          .header(HttpHeaders.AUTHORIZATION, bearer())
          .retrieve()
          .toBodilessEntity();
      return true;
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        return false;
      }
      throw unavailable(e);
    } catch (RestClientException e) {
      throw unavailable(e);
    }
  }

  /**
   * Deletes a photo with its files; deleting a missing photo succeeds.
   *
   * @throws MediaUnavailableException when the photo could not be deleted
   */
  public void deletePhoto(UUID photoId) {
    delete(PHOTO, photoId);
  }

  /**
   * Deletes all photos of a property.
   *
   * @throws MediaUnavailableException when the photos could not be deleted
   */
  public void deletePropertyPhotos(UUID propertyId) {
    delete("/internal/v1/photos?propertyId={propertyId}", propertyId);
  }

  private void delete(String path, UUID id) {
    try {
      http.delete()
          .uri(path, id)
          .header(HttpHeaders.AUTHORIZATION, bearer())
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      throw unavailable(e);
    }
  }

  private String bearer() {
    tokenLock.lock();
    try {
      Instant now = clock.instant();
      if (token == null || !now.isBefore(tokenRenewAt)) {
        IssuedServiceToken issued = tokens.issueOwn(List.of(SCOPE));
        token = issued.value();
        tokenRenewAt = now.plusSeconds(issued.expiresInSeconds()).minus(RENEW_MARGIN);
      }
      return "Bearer " + token;
    } finally {
      tokenLock.unlock();
    }
  }

  private static MediaUnavailableException unavailable(RestClientException e) {
    return new MediaUnavailableException("media-service call failed", e);
  }

  /** Metadata of a photo as returned by media-service; other fields are ignored. */
  public record PhotoMetadata(UUID id, UUID ownerId, UUID propertyId, String status) {}
}
