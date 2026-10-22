package edu.uoc.planner.auth.internal;

import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Interactive login → encrypted session (ADR 0002). */
@Service
final class LoginService {

  private static final Logger log = LoggerFactory.getLogger(LoginService.class);

  private final BrowserLogin browser;
  private final SessionStore store;
  private final AuthProperties properties;
  private final Clock clock;

  LoginService(BrowserLogin browser, SessionStore store, AuthProperties properties, Clock clock) {
    this.browser = browser;
    this.store = store;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * @return number of cookies stored
   */
  int login() {
    List<BrowserCookie> cookies =
        browser.capture(
            properties.canvasUrl(),
            properties.loginTimeout(),
            properties.pollInterval(),
            properties.headless());
    if (cookies.isEmpty()) {
      throw new LoginFailedException("No cookies for " + properties.canvasUrl().getHost());
    }
    String header =
        cookies.stream().map(c -> c.name() + "=" + c.value()).collect(Collectors.joining("; "));
    store.replace(header, clock.instant());
    log.info("Stored Canvas session with {} cookies", cookies.size());
    return cookies.size();
  }
}
