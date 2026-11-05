package dev.inspecto.core.property;

import java.util.UUID;

/**
 * Tells whether other modules refer to a room. Implemented by the inspection module, which depends
 * on this one; the interface keeps the dependency one-way.
 */
@FunctionalInterface
public interface RoomUsage {

  /** Whether an inspection checklist (and, from T-203, a defect) refers to the room. */
  boolean isUsed(UUID roomId);
}
