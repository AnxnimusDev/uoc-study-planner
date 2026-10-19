package edu.uoc.planner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@Import(PlannerApplicationTests.Containers.class)
class PlannerApplicationTests {

  @Test
  void contextLoads() {}

  @TestConfiguration(proxyBeanMethods = false)
  static class Containers {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
      return new PostgreSQLContainer<>("postgres:16-alpine");
    }
  }
}
