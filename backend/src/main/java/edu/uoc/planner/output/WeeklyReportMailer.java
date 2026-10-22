package edu.uoc.planner.output;

import edu.uoc.planner.output.internal.OutputProperties;
import edu.uoc.planner.planning.PlannedSession;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends the weekly PDF through the student's own SMTP (docs/design.md, section 9) to every address
 * in {@code planner.output.mail-to} (the UOC one and the personal one).
 */
@Component
public final class WeeklyReportMailer {

  private static final Logger log = LoggerFactory.getLogger(WeeklyReportMailer.class);
  private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d/MM");
  private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d/MM/yyyy");

  private final JavaMailSender sender;
  private final WeeklyReportPdf pdf;
  private final OutputProperties properties;

  public WeeklyReportMailer(
      JavaMailSender sender, WeeklyReportPdf pdf, OutputProperties properties) {
    this.sender = sender;
    this.pdf = pdf;
    this.properties = properties;
  }

  /**
   * @throws MailNotConfiguredException without recipients
   */
  public void send(WeeklyReport report) {
    List<String> to = properties.recipients();
    if (to.isEmpty()) {
      throw new MailNotConfiguredException(
          "No hay destinatarios: define PLANNER_MAIL_TO en .env (direcciones separadas por comas)");
    }
    MimeMessage message = sender.createMimeMessage();
    try {
      MimeMessageHelper mail = new MimeMessageHelper(message, true, "UTF-8");
      if (properties.mailFrom() != null && !properties.mailFrom().isBlank()) {
        mail.setFrom(properties.mailFrom());
      }
      mail.setTo(to.toArray(String[]::new));
      mail.setSubject("Plan de estudio · semana del " + SHORT.format(report.weekStart()));
      mail.setText(body(report), false);
      mail.addAttachment(
          "plan-" + report.weekStart() + ".pdf",
          new ByteArrayResource(pdf.render(report)),
          "application/pdf");
    } catch (MessagingException e) {
      throw new MailPreparationException("Cannot build the weekly email", e);
    }
    sender.send(message);
    log.info("Weekly plan for {} sent to {} recipients", report.weekStart(), to.size());
  }

  private static String body(WeeklyReport report) {
    Duration total =
        report.sessions().stream()
            .map(PlannedSession::duration)
            .reduce(Duration.ZERO, Duration::plus);
    int sessions = report.sessions().size();
    int deadlines = report.deadlines().size();
    int risks = report.atRisk().size();
    return "Hola,\n\nAdjunto tienes el plan de la semana del "
        + LONG.format(report.weekStart())
        + ": "
        + plural(sessions, "sesión", "sesiones")
        + " ("
        + WeeklyReportPdf.hours(total)
        + "), "
        + plural(deadlines, "entrega", "entregas")
        + " y "
        + plural(risks, "reto en riesgo", "retos en riesgo")
        + ".\n\nRevisa la política de IA y la lista de formato de cada reto en el PDF.\n\n"
        + "UOC Study Planner\n";
  }

  private static String plural(int count, String one, String many) {
    return count + " " + (count == 1 ? one : many);
  }
}
