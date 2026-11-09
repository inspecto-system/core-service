package dev.inspecto.core.suggestion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence of category suggestions. */
public interface MlSuggestionRepository extends JpaRepository<MlSuggestion, UUID> {

  /** A suggestion of the user. */
  Optional<MlSuggestion> findByIdAndRequestedBy(UUID id, UUID requestedBy);
}
