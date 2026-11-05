package dev.inspecto.core.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.dictionary.ElementGroup;
import dev.inspecto.core.inspection.ChecklistItem.ChecklistEntry;
import dev.inspecto.core.inspection.ChecklistItem.ChecklistItemDraft;
import java.time.Instant;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class InspectionDomainTest {

  private static final Instant CREATED = Instant.parse("2026-11-05T10:00:00Z");
  private static final Instant LATER = Instant.parse("2026-11-05T12:00:00Z");
  private static final InspectionFields FIELDS = new InspectionFields("Приёмка", CREATED, null);

  @Test
  void shouldCreateDraft() {
    Inspection inspection = inspection(InspectionStatus.DRAFT);

    assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.DRAFT);
    assertThat(inspection.getCompletedAt()).isNull();
    assertThat(inspection.fields()).isEqualTo(FIELDS);
  }

  @Test
  void shouldStartDraftAndKeepStartedInspectionInProgress() {
    Inspection inspection = inspection(InspectionStatus.DRAFT);

    inspection.update(FIELDS, true, LATER);
    assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.IN_PROGRESS);
    assertThat(inspection.getUpdatedAt()).isEqualTo(LATER);

    inspection.update(FIELDS, true, LATER);
    assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.IN_PROGRESS);
  }

  @Test
  void shouldChangeFieldsWithoutStarting() {
    Inspection inspection = inspection(InspectionStatus.DRAFT);
    InspectionFields changed = new InspectionFields(null, LATER, "Заметки");

    inspection.update(changed, false, LATER);

    assertThat(inspection.fields()).isEqualTo(changed);
    assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.DRAFT);
  }

  @Test
  void shouldCompleteInspectionInProgress() {
    Inspection inspection = inspection(InspectionStatus.IN_PROGRESS);

    inspection.complete(LATER);

    assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.COMPLETED);
    assertThat(inspection.getCompletedAt()).isEqualTo(LATER);
  }

  @ParameterizedTest
  @EnumSource(
      value = InspectionStatus.class,
      names = {"DRAFT", "COMPLETED"})
  void shouldCompleteOnlyInspectionInProgress(InspectionStatus status) {
    Inspection inspection = inspection(status);

    assertTransitionError(() -> inspection.complete(LATER));
  }

  @Test
  void shouldForbidChangesOfCompletedInspection() {
    Inspection inspection = inspection(InspectionStatus.COMPLETED);

    assertTransitionError(() -> inspection.update(FIELDS, false, LATER));
    assertTransitionError(inspection::requireNotCompleted);
  }

  @ParameterizedTest
  @EnumSource(
      value = InspectionStatus.class,
      names = {"DRAFT", "IN_PROGRESS"})
  void shouldAllowChecklistChangesBeforeCompletion(InspectionStatus status) {
    assertThatCode(inspection(status)::requireNotCompleted).doesNotThrowAnyException();
  }

  @Test
  void shouldDeleteOnlyDraft() {
    assertThatCode(inspection(InspectionStatus.DRAFT)::requireDeletable).doesNotThrowAnyException();
    assertTransitionError(inspection(InspectionStatus.IN_PROGRESS)::requireDeletable);
    assertTransitionError(inspection(InspectionStatus.COMPLETED)::requireDeletable);
  }

  @Test
  void shouldCreateUncheckedItemAndRecordResult() {
    UUID templateItemId = UUID.randomUUID();
    ChecklistItemDraft draft =
        new ChecklistItemDraft(UUID.randomUUID(), templateItemId, ElementGroup.WALLS, "Стены", 0);
    ChecklistItem item = new ChecklistItem(UUID.randomUUID(), UUID.randomUUID(), draft, CREATED);

    assertThat(item.entry()).isEqualTo(new ChecklistEntry(ChecklistResult.NOT_CHECKED, null));
    assertThat(item.isCustom()).isFalse();

    ChecklistEntry entry = new ChecklistEntry(ChecklistResult.DEFECT, "Трещина у окна");
    item.record(entry, LATER);
    assertThat(item.entry()).isEqualTo(entry);
  }

  @Test
  void shouldMarkItemWithoutTemplateAsCustom() {
    ChecklistItemDraft draft =
        new ChecklistItemDraft(UUID.randomUUID(), null, ElementGroup.OTHER, "Кондиционер", 8);
    ChecklistItem item = new ChecklistItem(UUID.randomUUID(), UUID.randomUUID(), draft, CREATED);

    assertThat(item.isCustom()).isTrue();
  }

  private static Inspection inspection(InspectionStatus status) {
    Inspection inspection =
        new Inspection(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), FIELDS, CREATED);
    if (status != InspectionStatus.DRAFT) {
      inspection.update(FIELDS, true, CREATED);
    }
    if (status == InspectionStatus.COMPLETED) {
      inspection.complete(CREATED);
    }
    return inspection;
  }

  private static void assertTransitionError(ThrowingCallable action) {
    assertThatThrownBy(action)
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).code())
        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
  }
}
