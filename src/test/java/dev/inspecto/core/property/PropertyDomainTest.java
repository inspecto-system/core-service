package dev.inspecto.core.property;

import static org.assertj.core.api.Assertions.assertThat;

import dev.inspecto.core.property.PropertyDtos.UpdatePropertyRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PropertyDomainTest {

  private static final Instant CREATED = Instant.parse("2026-11-05T10:00:00Z");
  private static final Instant LATER = Instant.parse("2026-11-05T11:00:00Z");
  private static final PropertyFields FLAT =
      new PropertyFields(
          "Квартира", PropertyType.APARTMENT, "ул. Мира, 5", "Казань", BigDecimal.TEN, "Заметка");

  @Test
  void shouldKeepAllFieldsWhenNothingIsSent() {
    assertThat(new UpdatePropertyRequest().applyTo(FLAT)).isEqualTo(FLAT);
  }

  @Test
  void shouldReplaceSentFieldsAndClearBlankNullableText() {
    UpdatePropertyRequest request = new UpdatePropertyRequest();
    request.setType(PropertyType.HOUSE);
    request.setAddress(" пос. Лесной ");
    request.setAreaSqm(null);
    request.setNotes("   ");

    PropertyFields result = request.applyTo(FLAT);

    assertThat(result)
        .isEqualTo(
            new PropertyFields(
                "Квартира", PropertyType.HOUSE, "пос. Лесной", "Казань", null, null));
  }

  @Test
  void shouldNotTouchActivePropertyOnUnarchive() {
    Property property = new Property(UUID.randomUUID(), UUID.randomUUID(), FLAT, CREATED);

    property.unarchive(LATER);

    assertThat(property.getArchivedAt()).isNull();
    assertThat(property.getUpdatedAt()).isEqualTo(CREATED);
  }

  @Test
  void shouldKeepFirstArchiveTime() {
    Property property = new Property(UUID.randomUUID(), UUID.randomUUID(), FLAT, CREATED);

    property.archive(CREATED);
    property.archive(LATER);

    assertThat(property.getArchivedAt()).isEqualTo(CREATED);
  }

  @Test
  void shouldUpdateOnlyGivenRoomFields() {
    Room room =
        new Room(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new RoomFields("Кухня", RoomType.KITCHEN, 0),
            CREATED);

    room.update(new RoomFields(null, null, null), LATER);
    assertThat(room.getName()).isEqualTo("Кухня");
    assertThat(room.getRoomType()).isEqualTo(RoomType.KITCHEN);
    assertThat(room.getSortOrder()).isZero();

    room.update(new RoomFields("Столовая", RoomType.LIVING, 3), LATER);
    assertThat(room.getName()).isEqualTo("Столовая");
    assertThat(room.getRoomType()).isEqualTo(RoomType.LIVING);
    assertThat(room.getSortOrder()).isEqualTo(3);
  }
}
