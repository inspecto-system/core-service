package dev.inspecto.core.outbox;

/** Kinds of deferred work handed to other services (ADR-018). */
public enum OutboxEventType {
  /** Remove photos and reports of a deleted property in media-service and report-service. */
  DELETE_PROPERTY_FILES
}
