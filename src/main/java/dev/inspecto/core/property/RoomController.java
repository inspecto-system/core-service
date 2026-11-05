package dev.inspecto.core.property;

import dev.inspecto.core.property.PropertyDtos.CreateRoomRequest;
import dev.inspecto.core.property.PropertyDtos.RoomResponse;
import dev.inspecto.core.property.PropertyDtos.UpdateRoomRequest;
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

/** Rooms of the user's properties (platform/api/core.yaml, tag rooms). */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('INSPECTOR', 'ADMIN')")
public class RoomController {

  private final RoomService rooms;

  /** Creates the controller. */
  public RoomController(RoomService rooms) {
    this.rooms = rooms;
  }

  /** Rooms of a property. */
  @GetMapping("/properties/{propertyId}/rooms")
  public List<RoomResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID propertyId) {
    return rooms.list(propertyId, CurrentUser.id(jwt)).stream().map(RoomResponse::of).toList();
  }

  /** Adds a room. */
  @PostMapping("/properties/{propertyId}/rooms")
  @ResponseStatus(HttpStatus.CREATED)
  public RoomResponse create(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID propertyId,
      @Valid @RequestBody CreateRoomRequest request) {
    RoomFields draft =
        new RoomFields(request.name().trim(), request.roomType(), request.sortOrder());
    return RoomResponse.of(rooms.create(propertyId, CurrentUser.id(jwt), draft));
  }

  /** Updates a room. */
  @PatchMapping("/rooms/{roomId}")
  public RoomResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID roomId,
      @Valid @RequestBody UpdateRoomRequest request) {
    String name = request.name() == null ? null : request.name().trim();
    RoomFields change = new RoomFields(name, request.roomType(), request.sortOrder());
    return RoomResponse.of(rooms.update(roomId, CurrentUser.id(jwt), change, request.version()));
  }

  /** Deletes a room. */
  @DeleteMapping("/rooms/{roomId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID roomId) {
    rooms.delete(roomId, CurrentUser.id(jwt));
  }
}
