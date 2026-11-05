package dev.inspecto.core.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.inspecto.core.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** JSON logs without secrets (CODE_QUALITY.md §1, SECURITY.md §5). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingIntegrationTest {

  private static final Logger LOG = LoggerFactory.getLogger(StructuredLoggingIntegrationTest.class);

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldWriteJsonAndMaskSensitiveMembers(CapturedOutput output) {
    String value = "value-" + UUID.randomUUID();

    MDC.put("refreshToken", value);
    MDC.put("userId", "u-1");
    try {
      LOG.atInfo().addKeyValue("Authorization", value).log("masking check");
    } finally {
      MDC.remove("refreshToken");
      MDC.remove("userId");
    }

    String line =
        output.getOut().lines().filter(l -> l.contains("masking check")).findFirst().orElseThrow();
    assertThat(line).startsWith("{").contains("\"refreshToken\":\"***\"").contains("\"u-1\"");
    assertThat(line).doesNotContain(value);
  }

  @Test
  void shouldNotLogPasswordsOfFailedSignIns(CapturedOutput output) throws Exception {
    String password = "Pw-" + UUID.randomUUID();
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"nobody-"
                        + UUID.randomUUID()
                        + "@example.com\",\"password\":\""
                        + password
                        + "\",\"client\":\"android\"}"))
        .andExpect(status().isUnauthorized());

    assertThat(output.getAll()).doesNotContain(password);
  }
}
