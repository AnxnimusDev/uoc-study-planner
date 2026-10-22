package edu.uoc.planner.output;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import edu.uoc.planner.extraction.ChallengeSpec.AiPolicyLevel;
import edu.uoc.planner.planning.PlannedSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/** Weekly plan as PDF: Thymeleaf ({@code templates/weekly-plan.html}) → OpenHTMLtoPDF. */
@Component
public final class WeeklyReportPdf {

  static final Locale SPANISH = Locale.forLanguageTag("es-ES");
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d/M", SPANISH);
  private static final DateTimeFormatter DUE =
      DateTimeFormatter.ofPattern("EEEE d/M HH:mm", SPANISH);
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
  private static final DateTimeFormatter WEEK = DateTimeFormatter.ofPattern("d/MM/yyyy");

  private final ITemplateEngine templates;

  public WeeklyReportPdf(ITemplateEngine templates) {
    this.templates = templates;
  }

  public byte[] render(WeeklyReport report) {
    Context context = new Context(SPANISH);
    context.setVariable("view", view(report));
    String html = templates.process("weekly-plan", context);
    ByteArrayOutputStream pdf = new ByteArrayOutputStream();
    try {
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.useFastMode();
      builder.withW3cDocument(new W3CDom().fromJsoup(Jsoup.parse(html)), "/");
      builder.toStream(pdf);
      builder.run();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return pdf.toByteArray();
  }

  record View(
      String title,
      String week,
      List<DayView> days,
      List<DeadlineView> deadlines,
      List<RiskView> risks,
      List<String> notices,
      List<RulesView> rules) {}

  record DayView(String label, List<SessionView> sessions) {}

  record SessionView(String time, String course, String title) {}

  record DeadlineView(String when, String course, String title) {}

  record RiskView(String title, String course, String missing) {}

  record RulesView(
      String title, String course, String policy, String evidence, List<String> formats) {}

  static View view(WeeklyReport report) {
    ZoneId zone = report.zone();
    LocalDate end = report.weekStart().plusDays(7);
    List<DayView> days = new ArrayList<>();
    for (LocalDate day = report.weekStart(); day.isBefore(end); day = day.plusDays(1)) {
      LocalDate current = day;
      List<SessionView> sessions =
          report.sessions().stream()
              .filter(s -> s.start().atZone(zone).toLocalDate().equals(current))
              .sorted(Comparator.comparing(PlannedSession::start))
              .map(
                  s ->
                      new SessionView(
                          TIME.format(s.start().atZone(zone))
                              + "–"
                              + TIME.format(s.end().atZone(zone)),
                          s.courseCode(),
                          s.title()))
              .toList();
      days.add(new DayView(capitalize(DAY.format(day)), sessions));
    }
    Instant from = report.weekStart().atStartOfDay(zone).toInstant();
    Instant to = end.atStartOfDay(zone).toInstant();
    List<DeadlineView> deadlines =
        report.deadlines().stream()
            .filter(d -> !d.dueAt().isBefore(from) && d.dueAt().isBefore(to))
            .sorted(Comparator.comparing(DeliverableDeadline::dueAt))
            .map(
                d ->
                    new DeadlineView(DUE.format(d.dueAt().atZone(zone)), d.courseCode(), d.title()))
            .toList();
    List<RiskView> risks =
        report.atRisk().stream()
            .map(r -> new RiskView(r.title(), r.courseCode(), "faltan " + hours(r.missing())))
            .toList();
    List<RulesView> rules =
        report.rules().stream()
            .map(
                r ->
                    new RulesView(
                        r.title(),
                        r.courseCode(),
                        policy(r.aiPolicy()),
                        r.evidence(),
                        r.formatRequirements()))
            .toList();
    return new View(
        "Plan de estudio",
        "Semana del " + WEEK.format(report.weekStart()),
        days,
        deadlines,
        risks,
        report.notices().stream().map(n -> n.message()).toList(),
        rules);
  }

  static String hours(Duration duration) {
    long h = duration.toHours();
    long m = duration.toMinutesPart();
    if (h == 0) {
      return m + " min";
    }
    return m == 0 ? h + " h" : h + " h " + m + " min";
  }

  private static String policy(AiPolicyLevel level) {
    if (level == null) {
      return "Política de IA no indicada";
    }
    return switch (level) {
      case FORBIDDEN -> "IA no permitida";
      case LIMITED -> "IA permitida con límites";
      case ALLOWED -> "IA permitida";
      case NOT_STATED -> "El enunciado no menciona la IA";
    };
  }

  private static String capitalize(String text) {
    return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }
}
