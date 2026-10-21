package edu.uoc.planner.canvas.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.uoc.planner.canvas.CanvasSessionProvider;
import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CanvasProperties.class)
class CanvasConfiguration {

  /**
   * The session provider is resolved on every request: until {@code auth} provides one there is
   * simply no session (ADR 0005).
   */
  @Bean
  CanvasClient canvasClient(
      WebClient.Builder webClientBuilder,
      CanvasProperties properties,
      ObjectProvider<CanvasSessionProvider> sessionProvider,
      ApplicationEventPublisher events,
      ObjectMapper mapper,
      Clock clock) {
    CanvasSessionProvider sessions =
        () ->
            Optional.ofNullable(sessionProvider.getIfAvailable())
                .flatMap(CanvasSessionProvider::currentCookie);
    return new CanvasClient(
        CanvasWebClient.create(webClientBuilder.clone(), properties),
        properties,
        sessions,
        events,
        mapper,
        clock);
  }
}
