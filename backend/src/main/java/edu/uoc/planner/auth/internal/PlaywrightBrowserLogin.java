package edu.uoc.planner.auth.internal;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Opens Chromium on the UOC classroom and waits for the student to log in. The password is typed
 * into the real UOC page and never reaches this process. Playwright starts only when a login is
 * requested, so the rest of the application never downloads nor launches a browser.
 */
@Component
final class PlaywrightBrowserLogin implements BrowserLogin {

  private static final Logger log = LoggerFactory.getLogger(PlaywrightBrowserLogin.class);

  @Override
  public List<BrowserCookie> capture(
      URI canvasUrl, Duration timeout, Duration pollInterval, boolean headless) {
    String self = canvasUrl.resolve("/api/v1/users/self").toString();
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless))) {
      BrowserContext context = browser.newContext();
      Page page = context.newPage();
      page.navigate(canvasUrl.toString());
      long deadline = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < deadline) {
        if (page.isClosed()) {
          throw new LoginFailedException("The browser was closed before logging in");
        }
        if (isLoggedIn(context, self)) {
          log.info("Canvas session detected, capturing cookies");
          return context.cookies(canvasUrl.toString()).stream()
              .map(c -> new BrowserCookie(c.name, c.value))
              .toList();
        }
        page.waitForTimeout(pollInterval.toMillis());
      }
      throw new LoginFailedException("Login not completed within " + timeout.toMinutes() + " min");
    } catch (PlaywrightException e) {
      throw new LoginFailedException(
          "Browser error: " + e.getMessage().lines().findFirst().orElse(""), e);
    }
  }

  /** Same cookie jar as the page. A 200 HTML page (SSO redirect) does not count. */
  private static boolean isLoggedIn(BrowserContext context, String selfUrl) {
    APIResponse response = context.request().get(selfUrl);
    try {
      String type = response.headers().getOrDefault("content-type", "");
      return response.status() == 200 && type.contains("json");
    } finally {
      response.dispose();
    }
  }
}
