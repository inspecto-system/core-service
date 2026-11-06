package dev.inspecto.core.inspection;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Loads the {@link PropertyHistory} of a property in three queries. */
@Component
public class PropertyHistories {

  private final InspectionRepository inspections;
  private final DefectRepository defects;
  private final ObservationRepository observations;

  /** Creates the component. */
  public PropertyHistories(
      InspectionRepository inspections,
      DefectRepository defects,
      ObservationRepository observations) {
    this.inspections = inspections;
    this.defects = defects;
    this.observations = observations;
  }

  /** History of the property; the caller checks access first. */
  public PropertyHistory of(UUID propertyId) {
    return new PropertyHistory(
        inspections.findAllByPropertyId(propertyId),
        defects.findByPropertyIdOrderByCreatedAtAscIdAsc(propertyId),
        observations.findByPropertyId(propertyId));
  }
}
