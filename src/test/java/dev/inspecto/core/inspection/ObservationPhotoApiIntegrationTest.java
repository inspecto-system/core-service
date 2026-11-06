package dev.inspecto.core.inspection;

import static dev.inspecto.core.support.Fixtures.idOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.inspecto.core.TestcontainersConfiguration;
import dev.inspecto.core.outbox.OutboxEvent;
import dev.inspecto.core.outbox.OutboxEventRepository;
import dev.inspecto.core.outbox.OutboxEventType;
import dev.inspecto.core.support.ApiUsers;
import dev.inspecto.core.support.FakeMedia;
import dev.inspecto.core.support.Fixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Photos of observations through media-service (T-206). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ObservationPhotoApiIntegrationTest {

  private static final int MAX_PHOTOS = 2;
  private static final FakeMedia MEDIA = FakeMedia.start();

  @Autowired private MockMvc mockMvc;
  @Autowired private OutboxEventRepository outbox;

  private Fixtures api;
  private UUID owner;
  private UUID propertyId;
  private String inspectionId;
  private String observationId;
  private String defectId;

  @DynamicPropertySource
  static void media(DynamicPropertyRegistry registry) {
    registry.add("inspecto.media.url", MEDIA::url);
    registry.add("inspecto.media.max-photos-per-observation", () -> MAX_PHOTOS);
  }

  @AfterAll
  static void stopMedia() {
    MEDIA.close();
  }

  @BeforeEach
  void createObservation() throws Exception {
    MEDIA.failWith(0);
    MEDIA.clearCalls();
    api = new Fixtures(mockMvc, ApiUsers.newInspector(mockMvc));
    owner = UUID.fromString(JsonPath.read(api.read("/api/v1/users/me"), "$.id"));
    propertyId = UUID.fromString(api.property("Квартира"));
    String room = api.room(propertyId.toString(), "Кухня", 0);
    inspectionId =
        api.startedInspection(propertyId.toString(), "2026-11-05T09:00:00Z", List.of(room));
    String defect = api.defect(inspectionId, room, api.systemCategory(Fixtures.CRACK), "Трещина");
    defectId = idOf(defect);
    observationId = JsonPath.read(defect, "$.observations[0].id");
  }

  @Test
  void shouldAttachPhotoUploadedByOwnerAndShowItEverywhere() throws Exception {
    UUID photo = MEDIA.upload(owner, propertyId);

    attach(photo, " Общий вид ")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.photoId").value(photo.toString()))
        .andExpect(jsonPath("$.caption").value("Общий вид"))
        .andExpect(jsonPath("$.sortOrder").value(0));

    List<FakeMedia.Call> calls = MEDIA.calls();
    assertThat(calls).extracting(FakeMedia.Call::method).containsExactly("GET", "POST");
    assertThat(calls.get(1).path()).isEqualTo("/internal/v1/photos/" + photo + "/attach");
    assertThat(calls)
        .allSatisfy(call ->
            assertThat(call.tokenClaims())
                .contains("\"sub\":\"core-service\"")
                .contains("inspecto-internal")
                .contains("\"scope\":\"photos.manage\""));
    api.send(get("/api/v1/defects/" + defectId))
        .andExpect(jsonPath("$.observations[0].photos[0].photoId").value(photo.toString()));
    api.send(get("/api/v1/inspections/" + inspectionId))
        .andExpect(jsonPath("$.rooms[0].observations[0].photos[0].caption").value("Общий вид"));
  }

  @Test
  void shouldReturnSameLinkWhenAttachIsRepeated() throws Exception {
    UUID photo = MEDIA.upload(owner, propertyId);
    attach(photo, null).andExpect(status().isCreated());
    MEDIA.clearCalls();

    attach(photo, null).andExpect(status().isCreated()).andExpect(jsonPath("$.sortOrder").value(0));

    assertThat(MEDIA.calls()).isEmpty();
    api.send(get("/api/v1/defects/" + defectId))
        .andExpect(jsonPath("$.observations[0].photos.length()").value(1));
  }

  @Test
  void shouldRejectPhotosOfOthersOtherPropertiesOrUnknown() throws Exception {
    List<UUID> photos =
        List.of(
            MEDIA.upload(UUID.randomUUID(), propertyId),
            MEDIA.upload(owner, UUID.randomUUID()),
            UUID.randomUUID());
    for (UUID photo : photos) {
      attach(photo, null)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("photoId"))
          .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_ID"));
    }
    assertThat(MEDIA.calls()).extracting(FakeMedia.Call::method).doesNotContain("POST");
  }

  @Test
  void shouldLimitPhotosPerObservation() throws Exception {
    for (int i = 0; i < MAX_PHOTOS; i++) {
      attach(MEDIA.upload(owner, propertyId), null)
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.sortOrder").value(i));
    }

    attach(MEDIA.upload(owner, propertyId), null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PHOTO_LIMIT_EXCEEDED"));
  }

  @Test
  void shouldNotMovePhotoToAnotherObservation() throws Exception {
    UUID photo = MEDIA.upload(owner, propertyId);
    attach(photo, null).andExpect(status().isCreated());
    String room = api.room(propertyId.toString(), "Ванная", 1);
    String other =
        JsonPath.read(
            api.defect(inspectionId, room, api.systemCategory(Fixtures.CRACK), "Скол"),
            "$.observations[0].id");

    api.send(post("/api/v1/observations/" + other + "/photos").content(body(photo, null)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
  }

  @Test
  void shouldKeepNothingWhenMediaIsDown() throws Exception {
    UUID photo = MEDIA.upload(owner, propertyId);
    MEDIA.failWith(500);

    attach(photo, null)
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));

    MEDIA.failWith(0);
    api.send(get("/api/v1/defects/" + defectId))
        .andExpect(jsonPath("$.observations[0].photos").isEmpty());
  }

  @Test
  void shouldDetachPhotoAndScheduleItsDeletion() throws Exception {
    UUID photo = MEDIA.upload(owner, propertyId);
    attach(photo, null).andExpect(status().isCreated());

    api.send(delete(photoPath(photo))).andExpect(status().isNoContent());

    api.send(get("/api/v1/defects/" + defectId))
        .andExpect(jsonPath("$.observations[0].photos").isEmpty());
    assertThat(deletionsOf(photo)).hasSize(1);
    api.send(delete(photoPath(photo))).andExpect(status().isNotFound());
  }

  @Test
  void shouldFreezePhotosOfCompletedInspection() throws Exception {
    UUID photo = MEDIA.upload(owner, propertyId);
    attach(photo, null).andExpect(status().isCreated());
    api.send(post("/api/v1/inspections/" + inspectionId + "/complete").content("{\"version\":1}"))
        .andExpect(status().isOk());

    attach(MEDIA.upload(owner, propertyId), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    api.send(delete(photoPath(photo)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
  }

  @Test
  void shouldScheduleDeletionOfPhotosOfDeletedDraft() throws Exception {
    String room = api.room(propertyId.toString(), "Балкон", 2);
    String draft =
        idOf(api.inspection(propertyId.toString(), "2026-11-06T09:00:00Z", List.of(room)));
    String draftObservation =
        JsonPath.read(
            api.defect(draft, room, api.systemCategory(Fixtures.CRACK), "Скол"),
            "$.observations[0].id");
    UUID photo = MEDIA.upload(owner, propertyId);
    api.send(
            post("/api/v1/observations/" + draftObservation + "/photos").content(body(photo, null)))
        .andExpect(status().isCreated());

    api.send(delete("/api/v1/inspections/" + draft)).andExpect(status().isNoContent());

    assertThat(deletionsOf(photo)).hasSize(1);
  }

  @Test
  void shouldShowPhotosOfLastObservationInOpenDefects() throws Exception {
    UUID photo = MEDIA.upload(owner, propertyId);
    attach(photo, null).andExpect(status().isCreated());
    String room =
        JsonPath.read(api.read("/api/v1/inspections/" + inspectionId), "$.rooms[0].room.id");
    String repeat =
        api.startedInspection(propertyId.toString(), "2026-12-01T09:00:00Z", List.of(room));

    api.send(get("/api/v1/inspections/" + repeat + "/open-defects"))
        .andExpect(jsonPath("$[0].lastObservation.photos[*].photoId", contains(photo.toString())));
  }

  private List<OutboxEvent> deletionsOf(UUID photo) {
    return outbox.findAll().stream()
        .filter(e -> e.getType() == OutboxEventType.DELETE_PHOTO)
        .filter(e -> e.getPayload().contains(photo.toString()))
        .toList();
  }

  private ResultActions attach(UUID photo, String caption) throws Exception {
    return api.send(
        post("/api/v1/observations/" + observationId + "/photos").content(body(photo, caption)));
  }

  private String photoPath(UUID photo) {
    return "/api/v1/observations/" + observationId + "/photos/" + photo;
  }

  private static String body(UUID photo, String caption) {
    String captionJson = caption == null ? "" : ",\"caption\":\"" + caption + "\"";
    return "{\"photoId\":\"" + photo + "\"" + captionJson + "}";
  }
}
