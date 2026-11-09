package dev.inspecto.core.suggestion;

import dev.inspecto.core.suggestion.MlClient.Prediction;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Predictions of a suggestion as {@code jsonb}: {@code [{"code":"CRACK","probability":0.81}]}. */
@Converter
public class PredictionsConverter implements AttributeConverter<List<Prediction>, String> {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final TypeReference<List<Prediction>> TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(List<Prediction> predictions) {
    return predictions == null ? null : JSON.writeValueAsString(predictions);
  }

  @Override
  public List<Prediction> convertToEntityAttribute(String json) {
    return json == null ? null : JSON.readValue(json, TYPE);
  }
}
