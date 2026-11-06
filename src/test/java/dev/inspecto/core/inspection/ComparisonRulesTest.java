package dev.inspecto.core.inspection;

import static org.assertj.core.api.Assertions.assertThat;

import dev.inspecto.core.inspection.ComparisonRules.Classification;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Every combination of the comparison table (ADR-004, contract 0.6.0). */
class ComparisonRulesTest {

  @ParameterizedTest(name = "as of A {0}, as of B {1}, checked in B {2} -> {3} reopened={4}")
  @CsvSource(
      nullValues = "-",
      value = {
        // as of A, as of B, checked in B, expected state, reopened
        "OPEN,     RESOLVED, true,  RESOLVED,    false",
        "OPEN,     RESOLVED, false, RESOLVED,    false",
        "-,        RESOLVED, true,  RESOLVED,    false",
        "-,        RESOLVED, false, RESOLVED,    false",
        "RESOLVED, RESOLVED, true,  -,           false",
        "RESOLVED, RESOLVED, false, -,           false",
        "OPEN,     OPEN,     true,  REMAINING,   false",
        "OPEN,     OPEN,     false, NOT_CHECKED, false",
        "-,        OPEN,     true,  NEW,         false",
        "-,        OPEN,     false, NOT_CHECKED, false",
        "RESOLVED, OPEN,     true,  NEW,         true",
        "RESOLVED, OPEN,     false, NOT_CHECKED, false",
        "-,        -,        false, -,           false",
      })
  void shouldClassifyEveryCombination(
      DefectStatus asOfA,
      DefectStatus asOfB,
      boolean checkedInB,
      ComparisonState expected,
      boolean reopened) {
    Optional<Classification> result = ComparisonRules.classify(asOfA, asOfB, checkedInB);

    if (expected == null) {
      assertThat(result).isEmpty();
    } else {
      assertThat(result).contains(new Classification(expected, reopened));
    }
  }
}
