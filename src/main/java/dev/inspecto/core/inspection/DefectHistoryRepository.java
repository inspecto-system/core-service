package dev.inspecto.core.inspection;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence of the defect status history. */
public interface DefectHistoryRepository extends JpaRepository<DefectStatusChange, UUID> {

  /** History of a defect, oldest first; ids are time-ordered UUIDv7 and break ties. */
  List<DefectStatusChange> findByDefectIdOrderByChangedAtAscIdAsc(UUID defectId);
}
