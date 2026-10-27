package com.dgc.amigoinvisible.service;

import com.dgc.amigoinvisible.config.AmigoProperties;
import com.dgc.amigoinvisible.model.Assignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final JavaMailSender mailSender;
    private final AmigoProperties amigoProperties;

    /**
     * Sends one email per assignment. Errors are logged but never abort
     * the remaining sends. After all individual emails, sends an admin
     * summary to the configured sender account.
     */
    @Async
    public void sendAssignmentEmails(List<Assignment> assignments) {
        String from     = amigoProperties.getMail().getFrom();
        String fromName = amigoProperties.getMail().getFromName();

        int sent   = 0;
        int failed = 0;

        for (Assignment assignment : assignments) {
            String senderName   = assignment.sender().name();
            String receiverName = assignment.receiver().name();
            String senderEmail  = assignment.sender().email();

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(senderEmail);
            msg.setFrom(fromName + " <" + from + ">");
            msg.setSubject("Amigo Invisible - Tu asignación");
            msg.setText(
                "Hola " + senderName + ",\n\n" +
                "¡Tu amigo invisible es " + receiverName + "!\n\n" +
                "¡Mucha suerte con el regalo!\n\n" +
                "Amigo Invisible App"
            );

            try {
                mailSender.send(msg);
                log.info("Email enviado a {} ({})", senderName, senderEmail);
                sent++;
            } catch (MailException e) {
                log.error("Error al enviar email a {} ({}): {}", senderName, senderEmail, e.getMessage());
                failed++;
            }
        }

        sendAdminSummary(assignments, from, fromName, sent, failed);
    }

    /**
     * Sends a usage summary to the admin (the configured sender account)
     * so the organizer has a record of every draw that was executed.
     */
    private void sendAdminSummary(List<Assignment> assignments, String from,
                                  String fromName, int sent, int failed) {
        StringBuilder body = new StringBuilder();
        body.append("Sorteo realizado el ")
            .append(LocalDateTime.now().format(FORMATTER)).append("\n");
        body.append("Participantes: ").append(assignments.size()).append("\n");
        body.append("Emails enviados: ").append(sent);
        if (failed > 0) body.append("  |  Fallos: ").append(failed);
        body.append("\n\n");
        body.append("Asignaciones:\n");
        assignments.forEach(a ->
            body.append("  ")
                .append(a.sender().name())
                .append(" → ")
                .append(a.receiver().name())
                .append("\n")
        );

        SimpleMailMessage summary = new SimpleMailMessage();
        summary.setTo(from);
        summary.setFrom(fromName + " <" + from + ">");
        summary.setSubject("[Amigo Invisible] Sorteo realizado - " +
                LocalDateTime.now().format(FORMATTER));
        summary.setText(body.toString());

        try {
            mailSender.send(summary);
            log.info("Resumen de uso enviado al administrador ({})", from);
        } catch (MailException e) {
            log.error("No se pudo enviar el resumen al administrador: {}", e.getMessage());
        }
    }
}
