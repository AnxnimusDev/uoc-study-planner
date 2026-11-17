package edu.uoc.planner;

import static org.assertj.core.api.Assertions.assertThat;

import edu.uoc.planner.availability.internal.AvailabilityProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code make run}: the real web server (not a mock servlet context) starts with the default
 * profile path. With a servlet context, Spring resolved "../config/availability.yml" as a web
 * resource and the binding failed.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WebServerStartupIntegrationTest {

  @Autowired private AvailabilityProperties properties;

  @Test
  void bindsTheDefaultProfilePathRelativeToTheBackendFolder() {
    assertThat(properties.path()).isEqualTo(Path.of("../config/availability.yml"));
  }
}
