package edu.uoc.planner.auth.internal;

import java.io.PrintStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code make login}. */
@Component
@ConditionalOnProperty(name = "planner.command", havingValue = "login")
final class LoginCommand implements ApplicationRunner, ExitCodeGenerator {

  private final LoginService login;
  private final PrintStream out;
  private int exitCode;

  @Autowired
  LoginCommand(LoginService login) {
    this(login, System.out);
  }

  LoginCommand(LoginService login, PrintStream out) {
    this.login = login;
    this.out = out;
  }

  @Override
  public void run(ApplicationArguments args) {
    out.println(
        "Se abre el navegador. Inicia sesión en el aula de la UOC; se cerrará solo al terminar.");
    try {
      int cookies = login.login();
      out.println(
          "Sesión guardada (" + cookies + " cookies, cifradas). Ya puedes ejecutar make sync.");
      exitCode = 0;
    } catch (LoginFailedException e) {
      out.println("No se ha podido iniciar sesión: " + e.getMessage());
      exitCode = 1;
    }
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
