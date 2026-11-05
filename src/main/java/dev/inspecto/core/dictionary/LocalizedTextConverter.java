package dev.inspecto.core.dictionary;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.json.JsonMapper;

/** Stores {@link LocalizedText} in a {@code jsonb} column. */
@Converter
public class LocalizedTextConverter implements AttributeConverter<LocalizedText, String> {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Override
  public String convertToDatabaseColumn(LocalizedText text) {
    return text == null ? null : JSON.writeValueAsString(text);
  }

  @Override
  public LocalizedText convertToEntityAttribute(String json) {
    return json == null ? null : JSON.readValue(json, LocalizedText.class);
  }
}
