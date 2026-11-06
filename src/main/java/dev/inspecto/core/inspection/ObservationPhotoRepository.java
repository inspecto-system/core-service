package dev.inspecto.core.inspection;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence of photo links of observations. */
public interface ObservationPhotoRepository extends JpaRepository<ObservationPhoto, UUID> {

  Optional<ObservationPhoto> findByPhotoId(UUID photoId);

  List<ObservationPhoto> findByObservationIdInOrderBySortOrderAscIdAsc(
      Collection<UUID> observationIds);

  long countByObservationId(UUID observationId);

  /** Largest position among the photos of an observation, or {@code -1} without photos. */
  @Query(
      "select coalesce(max(p.sortOrder), -1) from ObservationPhoto p"
          + " where p.observationId = :observationId")
  int maxSortOrder(@Param("observationId") UUID observationId);

  /** Ids of the photos of all observations made in an inspection. */
  @Query(
      """
      select p.photoId from ObservationPhoto p, DefectObservation o
      where o.id = p.observationId and o.inspectionId = :inspectionId
      """)
  List<UUID> photoIdsOfInspection(@Param("inspectionId") UUID inspectionId);
}
