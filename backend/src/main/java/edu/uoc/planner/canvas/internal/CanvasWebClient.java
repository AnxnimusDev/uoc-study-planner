package edu.uoc.planner.canvas.internal;

import edu.uoc.planner.canvas.CanvasRequestRejectedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Builds the only {@link WebClient} that talks to Canvas. */
final class CanvasWebClient {

  static final String CANVAS_ACCEPT = "application/json+canvas-string-ids";

  private CanvasWebClient() {}

  static WebClient create(WebClient.Builder builder, CanvasProperties properties) {
    return builder
        .defaultHeader(HttpHeaders.ACCEPT, CANVAS_ACCEPT)
        .codecs(
            c -> c.defaultCodecs().maxInMemorySize((int) properties.maxResponseSize().toBytes()))
        .filter(readOnly())
        .build();
  }

  /** CLAUDE.md rule 1: anything other than GET fails before leaving the process. */
  static ExchangeFilterFunction readOnly() {
    return ExchangeFilterFunction.ofRequestProcessor(
        request ->
            HttpMethod.GET.equals(request.method())
                ? Mono.just(request)
                : Mono.error(
                    new CanvasRequestRejectedException(
                        "Refusing " + request.method() + ": the planner is read-only")));
  }
}
