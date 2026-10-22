package edu.uoc.planner.canvas.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.uoc.planner.canvas.CanvasRateLimitedException;
import edu.uoc.planner.canvas.CanvasRequestRejectedException;
import edu.uoc.planner.canvas.CanvasSessionExpiredEvent;
import edu.uoc.planner.canvas.CanvasSessionExpiredException;
import edu.uoc.planner.canvas.CanvasSessionProvider;
import edu.uoc.planner.canvas.CanvasUnavailableException;
import edu.uoc.planner.canvas.CanvasUnexpectedResponseException;
import edu.uoc.planner.canvas.internal.api.CanvasAssignment;
import edu.uoc.planner.canvas.internal.api.CanvasAssignmentGroup;
import edu.uoc.planner.canvas.internal.api.CanvasCourse;
import edu.uoc.planner.canvas.internal.api.CanvasModule;
import edu.uoc.planner.canvas.internal.api.CanvasPage;
import edu.uoc.planner.canvas.internal.api.CanvasTab;
import edu.uoc.planner.canvas.internal.api.CanvasUser;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * Read-only client for the Canvas REST API. Blocking: the daily sync runs in the background and
 * gains nothing from a reactive pipeline.
 */
public final class CanvasClient {

  private static final Logger log = LoggerFactory.getLogger(CanvasClient.class);

  private static final String WHILE_PREFIX = "while(1);";
  private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>\\s*;\\s*rel=\"next\"");
  private static final int PER_PAGE = 100;
  private static final int MAX_PAGES = 100;

  private final WebClient webClient;
  private final CanvasProperties properties;
  private final CanvasSessionProvider sessions;
  private final ApplicationEventPublisher events;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final Retry retry;
  private final CircuitBreaker circuitBreaker;
  private final RateLimiter rateLimiter;

  CanvasClient(
      WebClient webClient,
      CanvasProperties properties,
      CanvasSessionProvider sessions,
      ApplicationEventPublisher events,
      ObjectMapper mapper,
      Clock clock) {
    this.webClient = webClient;
    this.properties = properties;
    this.sessions = sessions;
    this.events = events;
    this.mapper = mapper;
    this.clock = clock;
    this.retry =
        Retry.of(
            "canvas",
            RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .intervalFunction(
                    IntervalFunction.ofExponentialBackoff(properties.initialBackoff(), 2.0))
                .retryExceptions(CanvasRateLimitedException.class, CanvasUnavailableException.class)
                .build());
    this.circuitBreaker =
        CircuitBreaker.of(
            "canvas",
            CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordExceptions(
                    CanvasRateLimitedException.class, CanvasUnavailableException.class)
                .build());
    this.rateLimiter =
        RateLimiter.of(
            "canvas",
            RateLimiterConfig.custom()
                .limitForPeriod(properties.requestsPerSecond())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMinutes(1))
                .build());
  }

  public CanvasUser self() {
    return getOne(uri("/api/v1/users/self").build().toUri(), CanvasUser.class);
  }

  /** Active enrollments only. */
  public List<CanvasCourse> courses() {
    return getAll(
        uri("/api/v1/courses").queryParam("enrollment_state", "active"), CanvasCourse.class);
  }

  public List<CanvasModule> modules(long courseId) {
    return getAll(
        uri("/api/v1/courses/{course}/modules").queryParam("include[]", "items"),
        CanvasModule.class,
        courseId);
  }

  public CanvasPage page(long courseId, String pageUrl) {
    return getOne(
        uri("/api/v1/courses/{course}/pages/{page}")
            .encode()
            .buildAndExpand(courseId, pageUrl)
            .toUri(),
        CanvasPage.class);
  }

  public List<CanvasAssignment> assignments(long courseId) {
    return getAll(uri("/api/v1/courses/{course}/assignments"), CanvasAssignment.class, courseId);
  }

  /** One assignment: for those a module links to but the course list did not return. */
  public CanvasAssignment assignment(long courseId, long assignmentId) {
    return getOne(
        uri("/api/v1/courses/{course}/assignments/{assignment}")
            .encode()
            .buildAndExpand(courseId, assignmentId)
            .toUri(),
        CanvasAssignment.class);
  }

  public List<CanvasAssignmentGroup> assignmentGroups(long courseId) {
    return getAll(
        uri("/api/v1/courses/{course}/assignment_groups"), CanvasAssignmentGroup.class, courseId);
  }

  public List<CanvasTab> tabs(long courseId) {
    return getAll(uri("/api/v1/courses/{course}/tabs"), CanvasTab.class, courseId);
  }

  private UriComponentsBuilder uri(String path) {
    return UriComponentsBuilder.fromUri(properties.baseUrl()).path(path);
  }

  private <T> T getOne(URI uri, Class<T> type) {
    return parse(get(uri).body(), mapper.constructType(type));
  }

  private <T> List<T> getAll(UriComponentsBuilder builder, Class<T> type, Object... variables) {
    URI next = builder.queryParam("per_page", PER_PAGE).encode().buildAndExpand(variables).toUri();
    JavaType listType = mapper.getTypeFactory().constructCollectionType(List.class, type);
    List<T> all = new ArrayList<>();
    Set<URI> visited = new HashSet<>();
    while (next != null) {
      if (!visited.add(next) || visited.size() > MAX_PAGES) {
        throw new CanvasUnexpectedResponseException("Pagination loop at " + next.getPath());
      }
      Response response = get(next);
      all.addAll(parse(response.body(), listType));
      next = nextPage(response.link());
    }
    return all;
  }

  private Response get(URI uri) {
    String cookie =
        sessions
            .currentCookie()
            .orElseThrow(() -> new CanvasSessionExpiredException("No Canvas session stored"));
    Supplier<Response> call =
        Retry.decorateSupplier(
            retry,
            CircuitBreaker.decorateSupplier(
                circuitBreaker,
                RateLimiter.decorateSupplier(rateLimiter, () -> send(uri, cookie))));
    try {
      return call.get();
    } catch (CallNotPermittedException | RequestNotPermitted e) {
      throw new CanvasUnavailableException("Canvas calls suspended: " + e.getMessage(), e);
    } catch (CanvasSessionExpiredException e) {
      events.publishEvent(new CanvasSessionExpiredEvent(clock.instant()));
      throw e;
    }
  }

  private Response send(URI uri, String cookie) {
    log.debug("GET {}", uri.getPath());
    return webClient
        .get()
        .uri(uri)
        .header(HttpHeaders.COOKIE, cookie)
        .exchangeToMono(
            response -> {
              int status = response.statusCode().value();
              if (response.statusCode().is2xxSuccessful()) {
                String link = response.headers().asHttpHeaders().getFirst(HttpHeaders.LINK);
                return response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(body -> new Response(body, link));
              }
              return response.releaseBody().then(Mono.error(failure(status, uri)));
            })
        .timeout(properties.timeout())
        .onErrorMap(
            TimeoutException.class,
            e -> new CanvasUnavailableException("Timeout on " + uri.getPath(), e))
        .onErrorMap(
            WebClientRequestException.class,
            e -> new CanvasUnavailableException("Cannot reach Canvas at " + uri.getPath(), e))
        .block();
  }

  private static RuntimeException failure(int status, URI uri) {
    String where = status + " on " + uri.getPath();
    if (status == 401) {
      return new CanvasSessionExpiredException("Canvas session expired: " + where);
    }
    if (status == 429) {
      return new CanvasRateLimitedException("Rate limited: " + where);
    }
    if (status >= 500) {
      return new CanvasUnavailableException("Canvas error: " + where);
    }
    return new CanvasUnexpectedResponseException("Unexpected response: " + where);
  }

  private <T> T parse(String body, JavaType type) {
    String json = body.startsWith(WHILE_PREFIX) ? body.substring(WHILE_PREFIX.length()) : body;
    try {
      return mapper.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new CanvasUnexpectedResponseException("Cannot parse " + type.getTypeName(), e);
    }
  }

  /** Follows {@code rel="next"} only within the Canvas origin, so the cookie never leaves it. */
  private URI nextPage(String link) {
    if (link == null) {
      return null;
    }
    Matcher matcher = NEXT_LINK.matcher(link);
    if (!matcher.find()) {
      return null;
    }
    URI next = URI.create(matcher.group(1));
    URI base = properties.baseUrl();
    boolean sameOrigin =
        Objects.equals(next.getScheme(), base.getScheme())
            && Objects.equals(next.getHost(), base.getHost())
            && next.getPort() == base.getPort();
    if (!sameOrigin) {
      throw new CanvasRequestRejectedException("Next page outside Canvas: " + next.getHost());
    }
    return next;
  }

  private record Response(String body, String link) {}
}
