package com.dgc.amigoinvisible.service;

import com.dgc.amigoinvisible.config.AmigoProperties;
import com.dgc.amigoinvisible.model.Assignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class EmailService {

    private static final String RESEND_URL = "https://api.resend.com/emails";
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String apiKey;
    private final AmigoProperties amigoProperties;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public EmailService(@Value("${resend.api-key}") String apiKey,
                        AmigoProperties amigoProperties) {
        this.apiKey = apiKey;
        this.amigoProperties = amigoProperties;
        log.info("EmailService: Resend HTTP client ready");
    }

    @Async
    public void sendAssignmentEmails(List<Assignment> assignments) {
        log.info("EmailService: async started, {} assignments", assignments.size());
        try {
            String adminEmail = amigoProperties.getMail().getFrom();
            String fromName   = amigoProperties.getMail().getFromName();
            // Resend allows sending from onboarding@resend.dev without domain verification
            String fromAddr   = fromName + " <onboarding@resend.dev>";
            log.info("EmailService: from={}, adminEmail={}", fromAddr, adminEmail);

            int sent = 0, failed = 0;
            for (Assignment assignment : assignments) {
                String senderName   = assignment.sender().name();
                String receiverName = assignment.receiver().name();
                String senderEmail  = assignment.sender().email();

                String body = "Hola " + senderName + ",\n\n"
                        + "Tu amigo invisible es " + receiverName + "!\n\n"
                        + "Mucha suerte con el regalo!\n\n"
                        + "Amigo Invisible App";

                if (sendEmail(fromAddr, senderEmail,
                        "Amigo Invisible - Tu asignacion", body)) {
                    log.info("EmailService: sent OK to {} ({})", senderName, senderEmail);
                    sent++;
                } else {
                    log.error("EmailService: FAILED to {} ({})", senderName, senderEmail);
                    failed++;
                }
            }
            log.info("EmailService: done - sent={}, failed={}", sent, failed);
            sendAdminSummary(assignments, fromAddr, adminEmail, sent, failed);
        } catch (Exception e) {
            log.error("EmailService: unexpected error", e);
        }
    }

    private boolean sendEmail(String from, String to, String subject, String text) {
        String json = "{"
                + "\"from\":\"" + esc(from) + "\","
                + "\"to\":[\"" + esc(to) + "\"],"
                + "\"subject\":\"" + esc(subject) + "\","
                + "\"text\":\"" + esc(text) + "\""
                + "}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            log.info("EmailService: Resend status={} body={}", response.statusCode(), response.body());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("EmailService: HTTP error sending to {}: {}", to, e.getMessage());
            return false;
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void sendAdminSummary(List<Assignment> assignments, String from,
                                   String adminEmail, int sent, int failed) {
        StringBuilder body = new StringBuilder();
        body.append("Sorteo realizado el ").append(LocalDateTime.now().format(FORMATTER)).append("\n");
        body.append("Participantes: ").append(assignments.size()).append("\n");
        body.append("Emails enviados: ").append(sent);
        if (failed > 0) body.append("  |  Fallos: ").append(failed);
        body.append("\n\nAsignaciones:\n");
        assignments.forEach(a -> body.append("  ")
                .append(a.sender().name()).append(" -> ")
                .append(a.receiver().name()).append("\n"));

        sendEmail(from, adminEmail,
                "[Amigo Invisible] Sorteo - " + LocalDateTime.now().format(FORMATTER),
                body.toString());
    }
}
