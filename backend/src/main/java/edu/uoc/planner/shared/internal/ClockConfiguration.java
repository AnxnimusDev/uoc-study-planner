package edu.uoc.planner.shared.internal;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Injectable clock, so time-dependent code can be tested with {@link Clock#fixed}. */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
