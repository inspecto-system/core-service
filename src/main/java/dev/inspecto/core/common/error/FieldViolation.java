package dev.inspecto.core.common.error;

/** A single invalid field of a request. */
public record FieldViolation(String field, String code) {}
