package dev.inspecto.core.report;

import dev.inspecto.core.security.OwnServiceToken;
import dev.inspecto.core.security.ServiceTokenService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Internal API of report-service (platform/api/report.yaml, tag internal). Calls carry a service
 * token that core-service signs itself with scope {@value #SCOPE}.
 */
@Component
@EnableConfigurationProperties(ReportProperties.class)
public class ReportClient {

  /** Scope required by the bulk deletion endpoint of report-service. */
  public static final String SCOPE = "reports.manage";

  private static final String REPORTS = "/internal/v1/reports";

  private final RestClient http;
  private final OwnServiceToken token;

  /** Creates the client. */
  public ReportClient(ServiceTokenService tokens, ReportProperties properties, Clock clock) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.timeout());
    factory.setReadTimeout(properties.timeout());
    this.http = RestClient.builder().baseUrl(properties.url()).requestFactory(factory).build();
    this.token = new OwnServiceToken(tokens, clock, SCOPE);
  }

  /**
   * Deletes all reports of a property with their files; deleting nothing succeeds.
   *
   * @throws ReportUnavailableException when the reports could not be deleted
   */
  public void deletePropertyReports(UUID propertyId) {
    delete(REPORTS + "?propertyId={propertyId}", propertyId);
  }

  /**
   * Deletes all reports requested by a user (a deleted account). In the MVP only the owner can read
   * a property, so these are all reports of the owner's properties as well.
   *
   * @throws ReportUnavailableException when the reports could not be deleted
   */
  public void deleteRequestedReports(UUID userId) {
    delete(REPORTS + "?requestedBy={userId}", userId);
  }

  private void delete(String path, UUID id) {
    try {
      http.delete()
          .uri(path, id)
          .header(HttpHeaders.AUTHORIZATION, token.bearer())
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      throw new ReportUnavailableException("report-service call failed", e);
    }
  }
}
