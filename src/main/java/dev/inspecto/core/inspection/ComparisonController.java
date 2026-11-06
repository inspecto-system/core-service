package dev.inspecto.core.inspection;

import dev.inspecto.core.dictionary.AppLocale;
import dev.inspecto.core.inspection.ComparisonDtos.InspectionComparisonResponse;
import dev.inspecto.core.security.CurrentUser;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Comparison of inspections (platform/api/core.yaml, tag comparison). */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('INSPECTOR', 'ADMIN')")
public class ComparisonController {

  private final InspectionComparisonService comparison;

  /** Creates the controller. */
  public ComparisonController(InspectionComparisonService comparison) {
    this.comparison = comparison;
  }

  /** Compares inspection A ({@code base}) with the later inspection B ({@code target}). */
  @GetMapping("/inspections/compare")
  public InspectionComparisonResponse compare(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam UUID base,
      @RequestParam UUID target,
      @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
    return comparison.compare(
        base, target, CurrentUser.id(jwt), AppLocale.fromAcceptLanguage(language));
  }
}
