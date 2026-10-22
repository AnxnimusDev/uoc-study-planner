package edu.uoc.planner.extraction.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExtractionProperties.class)
class ExtractionConfiguration {

  @Bean
  SpecModel specModel(ExtractionProperties properties) {
    return new AnthropicSpecModel(properties);
  }

  @Bean
  SpecValidator specValidator() {
    return new SpecValidator();
  }
}
