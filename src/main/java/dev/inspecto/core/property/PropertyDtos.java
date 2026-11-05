package dev.inspecto.core.property;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** Request and response bodies of the property and room API (platform/api/core.yaml). */
public final class PropertyDtos {

  /** At least one non-whitespace character; PATCH fields cannot use {@code @NotBlank}. */
  private static final String NOT_BLANK = "(?s).*\\S.*";

  private PropertyDtos() {}

  /** New property. */
  public record CreatePropertyRequest(
      @NotBlank @Size(max = 200) String name,
      @NotNull PropertyType type,
      @NotBlank @Size(max = 500) String address,
      @Size(max = 100) String city,
      @DecimalMin("0") @DecimalMax("100000") @Digits(integer = 6, fraction = 2) BigDecimal areaSqm,
      @Size(max = 4000) String notes) {

    /** Descriptive fields with trimmed text. */
    public PropertyFields toFields() {
      return new PropertyFields(
          name.trim(), type, address.trim(), trimToNull(city), areaSqm, trimToNull(notes));
    }
  }

  /**
   * Partial update. Absent fields keep their values. Required fields ignore {@code null}; for the
   * nullable fields ({@code city}, {@code areaSqm}, {@code notes}) an explicit JSON {@code null}
   * clears the value, so the setters record which of them were sent.
   */
  public static final class UpdatePropertyRequest {

    @Size(min = 1, max = 200)
    @Pattern(regexp = NOT_BLANK)
    private String name;

    private PropertyType type;

    @Size(min = 1, max = 500)
    @Pattern(regexp = NOT_BLANK)
    private String address;

    @Size(max = 100)
    private String city;

    @DecimalMin("0")
    @DecimalMax("100000")
    @Digits(integer = 6, fraction = 2)
    private BigDecimal areaSqm;

    @Size(max = 4000)
    private String notes;

    @NotNull
    @Min(0)
    private Long version;

    private boolean citySent;
    private boolean areaSqmSent;
    private boolean notesSent;

    public void setName(String name) {
      this.name = name;
    }

    public void setType(PropertyType type) {
      this.type = type;
    }

    public void setAddress(String address) {
      this.address = address;
    }

    /** Sets or, with {@code null}, clears the city. */
    public void setCity(String city) {
      this.city = city;
      this.citySent = true;
    }

    /** Sets or, with {@code null}, clears the area. */
    public void setAreaSqm(BigDecimal areaSqm) {
      this.areaSqm = areaSqm;
      this.areaSqmSent = true;
    }

    /** Sets or, with {@code null}, clears the notes. */
    public void setNotes(String notes) {
      this.notes = notes;
      this.notesSent = true;
    }

    public void setVersion(Long version) {
      this.version = version;
    }

    /** Version the client based the change on. */
    public Long version() {
      return version;
    }

    /** Applies the sent fields to the current ones. */
    public PropertyFields applyTo(PropertyFields current) {
      return new PropertyFields(
          name == null ? current.name() : name.trim(),
          type == null ? current.type() : type,
          address == null ? current.address() : address.trim(),
          citySent ? trimToNull(city) : current.city(),
          areaSqmSent ? areaSqm : current.areaSqm(),
          notesSent ? trimToNull(notes) : current.notes());
    }
  }

  /** Property as returned by the list and by modifying operations. */
  public record PropertyResponse(
      UUID id,
      String name,
      PropertyType type,
      String address,
      String city,
      BigDecimal areaSqm,
      String notes,
      Instant archivedAt,
      Instant createdAt,
      Instant updatedAt,
      long version) {

    /** Maps an entity to the response. */
    public static PropertyResponse of(Property property) {
      PropertyFields fields = property.fields();
      return new PropertyResponse(
          property.getId(),
          fields.name(),
          fields.type(),
          fields.address(),
          fields.city(),
          fields.areaSqm(),
          fields.notes(),
          property.getArchivedAt(),
          property.getCreatedAt(),
          property.getUpdatedAt(),
          property.getVersion());
    }
  }

  /**
   * Property card. Inspections and defects are introduced in T-201 and T-203; until then a property
   * has none, so the counters are zero.
   */
  public record PropertyDetailsResponse(
      UUID id,
      String name,
      PropertyType type,
      String address,
      String city,
      BigDecimal areaSqm,
      String notes,
      Instant archivedAt,
      Instant createdAt,
      Instant updatedAt,
      long version,
      List<RoomResponse> rooms,
      int inspectionCount,
      int openDefectCount,
      Instant lastInspectionAt) {

    /** Copies the room list. */
    public PropertyDetailsResponse {
      rooms = List.copyOf(rooms);
    }

    /** Maps a property and its rooms to the card. */
    public static PropertyDetailsResponse of(Property property, List<Room> rooms) {
      PropertyResponse base = PropertyResponse.of(property);
      return new PropertyDetailsResponse(
          base.id(),
          base.name(),
          base.type(),
          base.address(),
          base.city(),
          base.areaSqm(),
          base.notes(),
          base.archivedAt(),
          base.createdAt(),
          base.updatedAt(),
          base.version(),
          rooms.stream().map(RoomResponse::of).toList(),
          0,
          0,
          null);
    }
  }

  /** Page of properties. */
  public record PropertyPage(List<PropertyResponse> items, int page, int size, long total) {

    /** Copies the item list. */
    public PropertyPage {
      items = List.copyOf(items);
    }

    /** Maps a Spring Data page. */
    public static PropertyPage of(Page<Property> page) {
      return new PropertyPage(
          page.map(PropertyResponse::of).getContent(),
          page.getNumber(),
          page.getSize(),
          page.getTotalElements());
    }
  }

  /** New room; without {@code sortOrder} the room is added after the existing ones. */
  public record CreateRoomRequest(
      @NotBlank @Size(max = 100) String name,
      @NotNull RoomType roomType,
      @Min(0) Integer sortOrder) {}

  /** Partial room update; absent fields keep their values. */
  public record UpdateRoomRequest(
      @Size(min = 1, max = 100) @Pattern(regexp = NOT_BLANK) String name,
      RoomType roomType,
      @Min(0) Integer sortOrder,
      @NotNull @Min(0) Long version) {}

  /** Room as returned by the API. */
  public record RoomResponse(
      UUID id, UUID propertyId, String name, RoomType roomType, int sortOrder, long version) {

    /** Maps an entity to the response. */
    public static RoomResponse of(Room room) {
      return new RoomResponse(
          room.getId(),
          room.getPropertyId(),
          room.getName(),
          room.getRoomType(),
          room.getSortOrder(),
          room.getVersion());
    }
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
