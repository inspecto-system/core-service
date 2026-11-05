package dev.inspecto.core.dictionary;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read access to checklist templates. */
public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, UUID> {

  /** System templates and the user's own ones, with their items. */
  @Query(
      """
      select distinct t from ChecklistTemplate t left join fetch t.items
      where t.ownerId is null or t.ownerId = :userId
      order by t.code
      """)
  List<ChecklistTemplate> findVisible(@Param("userId") UUID userId);
}
