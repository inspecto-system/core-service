package dev.inspecto.core.inspection;

import dev.inspecto.core.inspection.DefectDtos.AttachPhotoRequest;
import dev.inspecto.core.inspection.DefectDtos.CreateObservationRequest;
import dev.inspecto.core.inspection.DefectDtos.ObservationPhotoResponse;
import dev.inspecto.core.inspection.DefectDtos.ObservationResponse;
import dev.inspecto.core.inspection.DefectDtos.OpenDefectResponse;
import dev.inspecto.core.inspection.DefectDtos.UpdateObservationRequest;
import dev.inspecto.core.inspection.ObservationService.ObservationUpdate;
import dev.inspecto.core.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Observations of defects in repeat inspections (platform/api/core.yaml, tag defects). */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('INSPECTOR', 'ADMIN')")
public class ObservationController {

  private final ObservationService observations;
  private final ObservationPhotoService photos;

  /** Creates the controller. */
  public ObservationController(ObservationService observations, ObservationPhotoService photos) {
    this.observations = observations;
    this.photos = photos;
  }

  /** Defects open before the inspection, to be marked in it. */
  @GetMapping("/inspections/{inspectionId}/open-defects")
  public List<OpenDefectResponse> openDefects(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return observations.openDefects(inspectionId, CurrentUser.id(jwt)).stream()
        .map(OpenDefectResponse::of)
        .toList();
  }

  /** Records an existing defect in the inspection. */
  @PostMapping("/inspections/{inspectionId}/observations")
  @ResponseStatus(HttpStatus.CREATED)
  public ObservationResponse create(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody CreateObservationRequest request) {
    return ObservationResponse.of(
        observations.create(inspectionId, CurrentUser.id(jwt), request.toNewObservation()));
  }

  /** Changes an observation. */
  @PatchMapping("/observations/{observationId}")
  public ObservationResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID observationId,
      @Valid @RequestBody UpdateObservationRequest request) {
    ObservationUpdate update = new ObservationUpdate(request::applyTo, request.version());
    return ObservationResponse.of(observations.update(observationId, CurrentUser.id(jwt), update));
  }

  /** Attaches an uploaded photo to the observation. */
  @PostMapping("/observations/{observationId}/photos")
  @ResponseStatus(HttpStatus.CREATED)
  public ObservationPhotoResponse attachPhoto(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID observationId,
      @Valid @RequestBody AttachPhotoRequest request) {
    return ObservationPhotoResponse.of(
        photos.attach(observationId, CurrentUser.id(jwt), request.toNewPhoto()));
  }

  /** Detaches a photo; its files are deleted by a clean-up job. */
  @DeleteMapping("/observations/{observationId}/photos/{photoId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void detachPhoto(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID observationId,
      @PathVariable UUID photoId) {
    photos.detach(observationId, CurrentUser.id(jwt), photoId);
  }
}
