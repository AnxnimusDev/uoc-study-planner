package edu.uoc.planner.planning.internal;

import edu.uoc.planner.planning.Planner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PlanningConfiguration {

  @Bean
  Planner planner() {
    return new GreedyPlanner();
  }
}
