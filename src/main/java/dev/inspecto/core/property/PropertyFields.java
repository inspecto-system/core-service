package dev.inspecto.core.property;

import java.math.BigDecimal;

/** Descriptive fields of a property, without identity and lifecycle data. */
public record PropertyFields(
    String name,
    PropertyType type,
    String address,
    String city,
    BigDecimal areaSqm,
    String notes) {}
