package dev.inspecto.core.property;

/** Room fields given by the client; {@code null} means "not given". */
public record RoomFields(String name, RoomType roomType, Integer sortOrder) {}
