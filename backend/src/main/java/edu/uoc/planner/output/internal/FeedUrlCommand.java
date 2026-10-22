package edu.uoc.planner.output.internal;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code make feed}: prints the subscription URLs. They carry the token, so stdout only. */
@Component
@ConditionalOnProperty(name = "planner.command", havingValue = "feed-url")
final class FeedUrlCommand implements ApplicationRunner, ExitCodeGenerator {

  private final FeedTokenStore tokens;
  private final OutputProperties properties;
  private List<String> urls = List.of();

  FeedUrlCommand(FeedTokenStore tokens, OutputProperties properties) {
    this.tokens = tokens;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    String base =
        properties.feedBase().toString().replaceAll("/+$", "") + "/calendar/" + tokens.token();
    urls = List.of(base + "/entregas.ics", base + "/plan.ics");
    System.out.println("Suscríbete a estos dos calendarios desde tu app de calendario.");
    System.out.println(
        "No compartas las URL: llevan tu token. Solo funcionan con make run activo.");
    System.out.println("  UOC · Entregas:        " + urls.get(0));
    System.out.println("  UOC · Plan de estudio: " + urls.get(1));
  }

  List<String> urls() {
    return urls;
  }

  @Override
  public int getExitCode() {
    return 0;
  }
}
