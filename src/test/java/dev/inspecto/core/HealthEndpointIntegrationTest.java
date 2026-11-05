package dev.inspecto.core;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class HealthEndpointIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldReportUpWhenDatabaseIsAvailable() throws Exception {
    mockMvc
        .perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void shouldExposeLivenessProbe() throws Exception {
    mockMvc.perform(get("/health/live")).andExpect(status().isOk());
  }

  @Test
  void shouldExposeReadinessProbe() throws Exception {
    mockMvc.perform(get("/health/ready")).andExpect(status().isOk());
  }

  @Test
  void shouldNotExposeEnvironmentEndpoint() throws Exception {
    mockMvc.perform(get("/env")).andExpect(status().is4xxClientError());
  }
}
