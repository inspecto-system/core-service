package dev.inspecto.core.inspection;

import java.util.List;

/** Observation with its photos in display order. */
public record ObservationView(DefectObservation observation, List<ObservationPhoto> photos) {

  /** Copies the photo list. */
  public ObservationView {
    photos = List.copyOf(photos);
  }

  /** A new observation, which has no photos yet. */
  public static ObservationView withoutPhotos(DefectObservation observation) {
    return new ObservationView(observation, List.of());
  }
}
