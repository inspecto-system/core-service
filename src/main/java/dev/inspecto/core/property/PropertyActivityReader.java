package dev.inspecto.core.property;

import java.util.UUID;

/**
 * Inspection activity of a property. Implemented by the inspection module, which depends on this
 * one; the interface keeps the dependency one-way.
 */
@FunctionalInterface
public interface PropertyActivityReader {

  /** Counters of the given property; the caller has already checked access to it. */
  PropertyActivity read(UUID propertyId);
}
