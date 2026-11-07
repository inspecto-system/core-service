package dev.inspecto.core.inspection;

import dev.inspecto.core.dictionary.AppLocale;
import dev.inspecto.core.inspection.ReportDataDtos.ReportDataResponse;
import dev.inspecto.core.security.ServiceClient;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Data of inspection reports for report-service (core.yaml, getReportData; ADR-026). */
@RestController
@RequestMapping("/internal/v1/inspections")
@PreAuthorize("hasAuthority('SCOPE_" + ServiceClient.REPORT_DATA + "')")
public class InternalReportController {

  private final ReportDataService reports;

  /** Creates the controller. */
  public InternalReportController(ReportDataService reports) {
    this.reports = reports;
  }

  /** Inspection aggregate for the PDF report, in Russian unless {@code locale=en}. */
  @GetMapping("/{inspectionId}/report-data")
  public ReportDataResponse reportData(
      @PathVariable UUID inspectionId,
      @RequestParam(defaultValue = "ru") @Pattern(regexp = "ru|en") String locale) {
    return reports.of(inspectionId, AppLocale.fromAcceptLanguage(locale));
  }
}
