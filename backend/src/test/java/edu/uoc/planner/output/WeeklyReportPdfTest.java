package edu.uoc.planner.output;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class WeeklyReportPdfTest {

  private final WeeklyReportPdf pdf = new WeeklyReportPdf(engine());

  @Test
  void rendersAPdf() {
    byte[] document = pdf.render(OutputSamples.report());

    assertThat(new String(document, 0, 5)).isEqualTo("%PDF-");
  }

  @Test
  void listsTheSevenDaysWithTheirSessions() throws Exception {
    String text = text(pdf.render(OutputSamples.report()));

    assertThat(text).contains("Plan de estudio", "Semana del 26/10/2026");
    assertThat(text).contains("Lunes 26/10", "Martes 27/10", "Domingo 1/11");
    assertThat(text).contains("18:15–19:00", "Llegir la guia d'estudi setmanal del Repte 1");
    assertThat(text).contains("Sin sesiones");
  }

  @Test
  void showsDeadlinesRisksAndNotices() throws Exception {
    String text = text(pdf.render(OutputSamples.report()));

    assertThat(text)
        .contains("Entregas de la semana", "QÜESTIONARI 2 - Setmana 3", "viernes 30/10 23:59");
    assertThat(text).contains("Retos en riesgo", "Repte 1. Investigació", "faltan 4 h 30 min");
    assertThat(text).contains("Graba el vídeo dentro de la ventana.");
  }

  @Test
  void quotesTheAiPolicyAndTheFormatChecklist() throws Exception {
    String text = text(pdf.render(OutputSamples.report()));

    assertThat(text).contains("IA no permitida");
    assertThat(text).contains("no està permès l’ús d’eines d’intel·ligència artificial");
    assertThat(text).contains("Fitxer PDF", "Portada amb nom i assignatura");
  }

  private static String text(byte[] document) throws Exception {
    try (PDDocument pdf = Loader.loadPDF(document)) {
      return new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
    }
  }

  static SpringTemplateEngine engine() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    SpringTemplateEngine engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);
    return engine;
  }
}
