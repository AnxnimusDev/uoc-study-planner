package edu.uoc.planner.output.internal;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code planner.output.*}.
 *
 * @param mailFrom sender of the weekly email; the SMTP account when empty
 * @param mailTo UOC and personal address
 * @param feedBaseUrl where calendar apps reach the feeds (the server only listens on 127.0.0.1)
 */
@ConfigurationProperties("planner.output")
public record OutputProperties(
    String mailFrom, List<String> mailTo, @DefaultValue("http://127.0.0.1:8080") URI feedBaseUrl) {

  public List<String> recipients() {
    return mailTo == null
        ? List.of()
        : mailTo.stream().map(String::strip).filter(a -> !a.isEmpty()).toList();
  }

  public URI feedBase() {
    return feedBaseUrl == null ? URI.create("http://127.0.0.1:8080") : feedBaseUrl;
  }
}
