package edu.uoc.planner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@Modulithic(sharedModules = "shared")
public class PlannerApplication {

  public static void main(String[] args) {
    SpringApplication.run(PlannerApplication.class, args);
  }
}
