package edu.uoc.planner.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.uoc.planner.output.internal.OutputProperties;
import jakarta.mail.Address;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class WeeklyReportMailerTest {

  private final JavaMailSender sender = mock(JavaMailSender.class);
  private final WeeklyReportPdf pdf = new WeeklyReportPdf(WeeklyReportPdfTest.engine());

  @Test
  void sendsThePdfToEveryConfiguredAddress() throws Exception {
    when(sender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

    mailer("alumne@uoc.edu", "personal@example.com").send(OutputSamples.report());

    ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender).send(sent.capture());
    MimeMessage message = sent.getValue();
    // The real sender calls this; it writes the part headers (file name, content type).
    message.saveChanges();
    assertThat(message.getSubject()).isEqualTo("Plan de estudio · semana del 26/10");
    assertThat(List.of(message.getRecipients(MimeMessage.RecipientType.TO)))
        .extracting(Address::toString)
        .containsExactly("alumne@uoc.edu", "personal@example.com");
    List<Part> parts = parts((Multipart) message.getContent());
    assertThat(parts)
        .anySatisfy(
            p -> {
              assertThat(p.getFileName()).isEqualTo("plan-2026-10-26.pdf");
              assertThat(p.getContentType()).startsWith("application/pdf");
            });
    assertThat(parts)
        .anySatisfy(
            p ->
                assertThat(String.valueOf(p.getContent()))
                    .contains("3 sesiones", "1 entrega", "1 reto en riesgo"));
  }

  @Test
  void refusesToSendWithoutRecipients() {
    assertThatThrownBy(() -> mailer().send(OutputSamples.report()))
        .isInstanceOf(MailNotConfiguredException.class)
        .hasMessageContaining("PLANNER_MAIL_TO");
    verify(sender, never()).send(any(MimeMessage.class));
  }

  private WeeklyReportMailer mailer(String... to) {
    return new WeeklyReportMailer(
        sender, pdf, new OutputProperties("planner@example.com", List.of(to), null));
  }

  private static List<Part> parts(Multipart multipart) throws Exception {
    List<Part> parts = new ArrayList<>();
    for (int i = 0; i < multipart.getCount(); i++) {
      Part part = multipart.getBodyPart(i);
      if (part.getContent() instanceof Multipart nested) {
        parts.addAll(parts(nested));
      } else {
        parts.add(part);
      }
    }
    return parts;
  }
}
