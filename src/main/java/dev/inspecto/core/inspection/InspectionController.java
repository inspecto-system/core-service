package dev.inspecto.core.inspection;

import dev.inspecto.core.dictionary.AppLocale;
import dev.inspecto.core.inspection.InspectionDtos.CreateInspectionRequest;
import dev.inspecto.core.inspection.InspectionDtos.InspectionDetailsResponse;
import dev.inspecto.core.inspection.InspectionDtos.InspectionPage;
import dev.inspecto.core.inspection.InspectionDtos.InspectionResponse;
import dev.inspecto.core.inspection.InspectionDtos.UpdateInspectionRequest;
import dev.inspecto.core.inspection.InspectionDtos.VersionOnly;
import dev.inspecto.core.inspection.InspectionLifecycleService.InspectionUpdate;
import dev.inspecto.core.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Inspections of the user's properties (platform/api/core.yaml, tag inspections). */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('INSPECTOR', 'ADMIN')")
public class InspectionController {

  private final InspectionService inspections;
  private final InspectionLifecycleService lifecycle;

  /** Creates the controller. */
  public InspectionController(InspectionService inspections, InspectionLifecycleService lifecycle) {
    this.inspections = inspections;
    this.lifecycle = lifecycle;
  }

  /** Inspection history of a property. */
  @GetMapping("/properties/{propertyId}/inspections")
  public InspectionPage list(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID propertyId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return InspectionPage.of(inspections.list(propertyId, CurrentUser.id(jwt), page, size));
  }

  /** Creates a draft inspection with its checklist. */
  @PostMapping("/properties/{propertyId}/inspections")
  @ResponseStatus(HttpStatus.CREATED)
  public InspectionDetailsResponse create(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID propertyId,
      @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language,
      @Valid @RequestBody CreateInspectionRequest request) {
    AppLocale locale = AppLocale.fromAcceptLanguage(language);
    return InspectionDetailsResponse.of(
        inspections.create(propertyId, CurrentUser.id(jwt), request.toNewInspection(), locale));
  }

  /** Inspection with its checklist. */
  @GetMapping("/inspections/{inspectionId}")
  public InspectionDetailsResponse get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return InspectionDetailsResponse.of(inspections.get(inspectionId, CurrentUser.id(jwt)));
  }

  /** Updates the descriptive fields or starts the inspection. */
  @PatchMapping("/inspections/{inspectionId}")
  public InspectionResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody UpdateInspectionRequest request) {
    InspectionUpdate update =
        new InspectionUpdate(request::applyTo, request.start(), request.version());
    return InspectionResponse.of(lifecycle.update(inspectionId, CurrentUser.id(jwt), update));
  }

  /** Completes the inspection. */
  @PostMapping("/inspections/{inspectionId}/complete")
  public InspectionResponse complete(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody VersionOnly request) {
    return InspectionResponse.of(
        lifecycle.complete(inspectionId, CurrentUser.id(jwt), request.version()));
  }

  /** Deletes a draft inspection. */
  @DeleteMapping("/inspections/{inspectionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    lifecycle.delete(inspectionId, CurrentUser.id(jwt));
  }
}
