package edu.uoc.planner.output.internal;

import edu.uoc.planner.output.CalendarSource;
import edu.uoc.planner.output.IcsCalendars;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutputProperties.class)
class OutputConfiguration {

  /** The calendar source is looked up per request: there may be none yet. */
  @Bean
  FeedController feedController(
      FeedTokenStore tokens, ObjectProvider<CalendarSource> source, IcsCalendars calendars) {
    return new FeedController(tokens, source::getIfAvailable, calendars);
  }
}
