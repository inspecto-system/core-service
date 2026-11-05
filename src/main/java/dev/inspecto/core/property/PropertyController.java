package dev.inspecto.core.property;

import dev.inspecto.core.property.PropertyDtos.CreatePropertyRequest;
import dev.inspecto.core.property.PropertyDtos.PropertyDetailsResponse;
import dev.inspecto.core.property.PropertyDtos.PropertyPage;
import dev.inspecto.core.property.PropertyDtos.PropertyResponse;
import dev.inspecto.core.property.PropertyDtos.UpdatePropertyRequest;
import dev.inspecto.core.property.PropertyService.PropertyCard;
import dev.inspecto.core.property.PropertyService.PropertyUpdate;
import dev.inspecto.core.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Properties of the signed-in user (platform/api/core.yaml, tag properties). */
@RestController
@RequestMapping("/api/v1/properties")
@PreAuthorize("hasAnyRole('INSPECTOR', 'ADMIN')")
public class PropertyController {

  private final PropertyService properties;

  /** Creates the controller. */
  public PropertyController(PropertyService properties) {
    this.properties = properties;
  }

  /** Own properties. */
  @GetMapping
  public PropertyPage list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "false") boolean archived,
      @RequestParam(required = false) @Size(max = 100) String query) {
    return PropertyPage.of(properties.list(CurrentUser.id(jwt), archived, query, page, size));
  }

  /** Creates a property. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PropertyResponse create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePropertyRequest request) {
    return PropertyResponse.of(properties.create(CurrentUser.id(jwt), request.toFields()));
  }

  /** Property card with rooms. */
  @GetMapping("/{propertyId}")
  public PropertyDetailsResponse get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID propertyId) {
    PropertyCard card = properties.get(propertyId, CurrentUser.id(jwt));
    return PropertyDetailsResponse.of(card.property(), card.rooms());
  }

  /** Updates a property. */
  @PatchMapping("/{propertyId}")
  public PropertyResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID propertyId,
      @Valid @RequestBody UpdatePropertyRequest request) {
    PropertyUpdate update = new PropertyUpdate(request::applyTo, request.version());
    return PropertyResponse.of(properties.update(propertyId, CurrentUser.id(jwt), update));
  }

  /** Deletes a property with all its data; files are removed asynchronously. */
  @DeleteMapping("/{propertyId}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID propertyId) {
    properties.delete(propertyId, CurrentUser.id(jwt));
  }

  /** Archives a property. */
  @PostMapping("/{propertyId}/archive")
  public PropertyResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID propertyId) {
    return PropertyResponse.of(properties.archive(propertyId, CurrentUser.id(jwt)));
  }

  /** Restores an archived property. */
  @PostMapping("/{propertyId}/unarchive")
  public PropertyResponse unarchive(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID propertyId) {
    return PropertyResponse.of(properties.unarchive(propertyId, CurrentUser.id(jwt)));
  }
}
