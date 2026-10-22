package edu.uoc.planner.auth.internal;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code planner.auth.*}.
 *
 * @param canvasUrl page opened for the login; cookies are captured for this origin
 * @param loginTimeout how long the student has to log in before giving up
 * @param pollInterval how often {@code /api/v1/users/self} is checked while waiting
 * @param headless only for tests: the student always sees the browser
 */
@ConfigurationProperties("planner.auth")
record AuthProperties(
    @DefaultValue("https://aula.uoc.edu") URI canvasUrl,
    @DefaultValue("10m") Duration loginTimeout,
    @DefaultValue("2s") Duration pollInterval,
    @DefaultValue("false") boolean headless) {}
