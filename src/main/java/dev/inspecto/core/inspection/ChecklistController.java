package dev.inspecto.core.inspection;

import dev.inspecto.core.inspection.ChecklistService.ChecklistUpdate;
import dev.inspecto.core.inspection.ChecklistService.CustomItem;
import dev.inspecto.core.inspection.InspectionDtos.ChecklistItemResponse;
import dev.inspecto.core.inspection.InspectionDtos.CreateChecklistItemRequest;
import dev.inspecto.core.inspection.InspectionDtos.UpdateChecklistItemRequest;
import dev.inspecto.core.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Checklists of inspections (platform/api/core.yaml, tag checklist). */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('INSPECTOR', 'ADMIN')")
public class ChecklistController {

  private final ChecklistService checklist;

  /** Creates the controller. */
  public ChecklistController(ChecklistService checklist) {
    this.checklist = checklist;
  }

  /** Adds a custom item. */
  @PostMapping("/inspections/{inspectionId}/checklist-items")
  @ResponseStatus(HttpStatus.CREATED)
  public ChecklistItemResponse addCustom(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @Valid @RequestBody CreateChecklistItemRequest request) {
    CustomItem item =
        new CustomItem(request.roomId(), request.elementGroup(), request.title().trim());
    return ChecklistItemResponse.of(checklist.addCustom(inspectionId, CurrentUser.id(jwt), item));
  }

  /** Records the result and the comment of an item. */
  @PatchMapping("/checklist-items/{checklistItemId}")
  public ChecklistItemResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID checklistItemId,
      @Valid @RequestBody UpdateChecklistItemRequest request) {
    ChecklistUpdate update = new ChecklistUpdate(request::applyTo, request.version());
    return ChecklistItemResponse.of(checklist.update(checklistItemId, CurrentUser.id(jwt), update));
  }
}
