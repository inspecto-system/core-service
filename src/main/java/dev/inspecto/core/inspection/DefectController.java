package dev.inspecto.core.inspection;

import dev.inspecto.core.dictionary.Severity;
import dev.inspecto.core.inspection.DefectDtos.CreateDefectRequest;
import dev.inspecto.core.inspection.DefectDtos.DefectDetailsResponse;
import dev.inspecto.core.inspection.DefectDtos.DefectPage;
import dev.inspecto.core.inspection.DefectDtos.DefectResponse;
import dev.inspecto.core.inspection.DefectDtos.DefectStatusChangeResponse;
import dev.inspecto.core.inspection.DefectDtos.UpdateDefectRequest;
import dev.inspecto.core.inspection.DefectService.DefectFilter;
import dev.inspecto.core.inspection.DefectService.DefectUpdate;
import dev.inspecto.core.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Defects of the user's properties (platform/api/core.yaml, tag defects). */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('INSPECTOR', 'ADMIN')")
public class DefectController {

  private final DefectService defects;

  /** Creates the controller. */
  public DefectController(DefectService defects) {
    this.defects = defects;
  }

  /** Defects of a property. */
  @GetMapping("/properties/{propertyId}/defects")
  public DefectPage list(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID propertyId,
      @Valid @ModelAttribute DefectQuery query) {
    DefectFilter filter = new DefectFilter(query.status(), query.roomId(), query.severity());
    return DefectPage.of(
        defects.list(propertyId, CurrentUser.id(jwt), filter, query.page(), query.size()));
  }

  /** Registers a new defect with its first observation. */
  @PostMapping("/inspections/{inspectionId}/defects")
  @ResponseStatus(HttpStatus.CREATED)
  public DefectDetailsResponse create(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody CreateDefectRequest request) {
    return DefectDetailsResponse.of(
        defects.create(inspectionId, CurrentUser.id(jwt), request.toNewDefect()));
  }

  /** Defect with its observations. */
  @GetMapping("/defects/{defectId}")
  public DefectDetailsResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID defectId) {
    return DefectDetailsResponse.of(defects.get(defectId, CurrentUser.id(jwt)));
  }

  /** Changes the category, title, description or severity. */
  @PatchMapping("/defects/{defectId}")
  public DefectResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID defectId,
      @Valid @RequestBody UpdateDefectRequest request) {
    DefectUpdate update = new DefectUpdate(request::applyTo, request.version());
    return DefectResponse.of(defects.update(defectId, CurrentUser.id(jwt), update));
  }

  /** Status history, oldest first. */
  @GetMapping("/defects/{defectId}/history")
  public List<DefectStatusChangeResponse> history(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID defectId) {
    return defects.history(defectId, CurrentUser.id(jwt)).stream()
        .map(DefectStatusChangeResponse::of)
        .toList();
  }

  /**
   * Query parameters of the defect list; absent paging parameters take their defaults.
   *
   * @param page page number, from 0
   * @param size page size, at most 100
   */
  public record DefectQuery(
      @Min(0) Integer page,
      @Min(1) @Max(100) Integer size,
      DefectStatus status,
      UUID roomId,
      Severity severity) {

    /** Applies the defaults. */
    public DefectQuery {
      page = page == null ? Integer.valueOf(0) : page;
      size = size == null ? Integer.valueOf(20) : size;
    }
  }
}
