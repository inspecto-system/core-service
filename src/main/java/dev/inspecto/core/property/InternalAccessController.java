package dev.inspecto.core.property;

import dev.inspecto.core.security.ServiceClient;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Access checks for media-service and report-service, which serve files of a property
 * (platform/api/core.yaml, checkPropertyAccess; SECURITY.md §3).
 */
@RestController
@RequestMapping("/internal/v1/access")
@PreAuthorize("hasAuthority('SCOPE_" + ServiceClient.ACCESS_CHECK + "')")
public class InternalAccessController {

  private final PropertyAccess access;

  /** Creates the controller. */
  public InternalAccessController(PropertyAccess access) {
    this.access = access;
  }

  /**
   * Allows the request when the user may perform the action on the property; otherwise {@code 404}
   * as for the user's own requests. In the MVP only the owner has access, and an owner may both
   * read and write; read-only access of clients arrives with {@code property_access}.
   */
  @GetMapping("/properties/{propertyId}")
  public AccessDecision checkProperty(
      @PathVariable UUID propertyId,
      @RequestParam UUID userId,
      @RequestParam @Pattern(regexp = "read|write") String action) {
    access.ownedProperty(propertyId, userId);
    return new AccessDecision(true);
  }

  /** Positive decision; a negative one is a {@code 404} response. */
  public record AccessDecision(boolean allowed) {}
}
