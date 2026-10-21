package edu.uoc.planner;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

/** CLAUDE.md rule 1: only GET requests ever reach Canvas. */
@AnalyzeClasses(packages = "edu.uoc.planner", importOptions = ImportOption.DoNotIncludeTests.class)
class ReadOnlyCanvasArchitectureTest {

  @ArchTest
  static final ArchRule noWritingWebClientCalls =
      noClasses()
          .should()
          .callMethod(WebClient.class, "post")
          .orShould()
          .callMethod(WebClient.class, "put")
          .orShould()
          .callMethod(WebClient.class, "patch")
          .orShould()
          .callMethod(WebClient.class, "delete")
          .orShould()
          .callMethod(WebClient.class, "method", HttpMethod.class)
          .because("the planner is read-only against Canvas");

  @ArchTest
  static final ArchRule canvasUsesOnlyWebClient =
      noClasses()
          .that()
          .resideInAPackage("edu.uoc.planner.canvas..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.web.client.RestTemplate")
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.web.client.RestClient")
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName("java.net.http.HttpClient")
          .because("every Canvas request must go through the read-only WebClient");
}
