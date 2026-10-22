package edu.uoc.planner;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@Modulithic(sharedModules = "shared")
public class PlannerApplication {

  public static void main(String[] args) {
    SpringApplication application = new SpringApplication(PlannerApplication.class);
    boolean command = Arrays.stream(args).anyMatch(a -> a.startsWith("--planner.command="));
    if (command) {
      // make login / make sync: run the command and exit, without opening port 8080.
      application.setWebApplicationType(WebApplicationType.NONE);
    }
    ConfigurableApplicationContext context = application.run(args);
    if (command) {
      System.exit(SpringApplication.exit(context));
    }
  }
}
