package edu.uoc.planner.shared.internal;

import edu.uoc.planner.shared.SecretCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CryptoConfiguration {

  /** Fails at startup when {@code PLANNER_SECRET_KEY} is missing or invalid. */
  @Bean
  SecretCipher secretCipher(@Value("${planner.secret-key:}") String secretKey) {
    return SecretCipher.fromBase64Key(secretKey);
  }
}
