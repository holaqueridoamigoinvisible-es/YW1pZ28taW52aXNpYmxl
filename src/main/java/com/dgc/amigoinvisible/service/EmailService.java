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
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class EmailService {

    private static final String MAILJET_URL = "https://api.mailjet.com/v3.1/send";
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String authorization;
    private final AmigoProperties amigoProperties;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public EmailService(@Value("${mailjet.api-key}") String apiKey,
                        @Value("${mailjet.secret-key}") String secretKey,
                        AmigoProperties amigoProperties) {
        this.authorization = "Basic " +
                Base64.getEncoder().encodeToString((apiKey + ":" + secretKey).getBytes());
        this.amigoProperties = amigoProperties;
        log.info("EmailService: Mailjet HTTP client ready (api key starts with {})",
                apiKey.substring(0, Math.min(6, apiKey.length())));
    }

    @Async
    public void sendAssignmentEmails(List<Assignment> assignments) {
        log.info("EmailService: async started, {} assignments", assignments.size());
        try {
            String from     = amigoProperties.getMail().getFrom();
            String fromName = amigoProperties.getMail().getFromName();
            log.info("EmailService: from={}", from);

            int sent = 0, failed = 0;
            for (Assignment assignment : assignments) {
                String senderName   = assignment.sender().name();
                String receiverName = assignment.receiver().name();
                String senderEmail  = assignment.sender().email();

                String body = "Hola " + senderName + ",\n\n"
                        + "Tu amigo invisible es " + receiverName + "!\n\n"
                        + "Mucha suerte con el regalo!\n\n"
                        + "Amigo Invisible App";

                if (sendEmail(from, fromName, senderEmail, senderName,
                        "Amigo Invisible - Tu asignacion", body)) {
                    log.info("EmailService: sent OK to {} ({})", senderName, senderEmail);
                    sent++;
                } else {
                    log.error("EmailService: FAILED to {} ({})", senderName, senderEmail);
                    failed++;
                }
            }
            log.info("EmailService: done - sent={}, failed={}", sent, failed);
            sendAdminSummary(assignments, from, fromName, sent, failed);
        } catch (Exception e) {
            log.error("EmailService: unexpected error", e);
        }
    }

    private boolean sendEmail(String fromAddr, String fromName,
                               String toAddr, String toName,
                               String subject, String text) {
        String json = "{"
                + "\"Messages\":[{"
                + "\"From\":{\"Email\":\"" + esc(fromAddr) + "\",\"Name\":\"" + esc(fromName) + "\"},"
                + "\"To\":[{\"Email\":\"" + esc(toAddr) + "\",\"Name\":\"" + esc(toName) + "\"}],"
                + "\"Subject\":\"" + esc(subject) + "\","
                + "\"TextPart\":\"" + esc(text) + "\""
                + "}]}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MAILJET_URL))
                    .header("Authorization", authorization)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            log.info("EmailService: Mailjet status={} body={}", response.statusCode(), response.body());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("EmailService: HTTP error sending to {}: {}", toAddr, e.getMessage());
            return false;
        }
    }

    /** Escapes characters that would break the JSON string. */
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void sendAdminSummary(List<Assignment> assignments, String from,
                                   String fromName, int sent, int failed) {
        StringBuilder body = new StringBuilder();
        body.append("Sorteo realizado el ").append(LocalDateTime.now().format(FORMATTER)).append("\n");
        body.append("Participantes: ").append(assignments.size()).append("\n");
        body.append("Emails enviados: ").append(sent);
        if (failed > 0) body.append("  |  Fallos: ").append(failed);
        body.append("\n\nAsignaciones:\n");
        assignments.forEach(a -> body.append("  ")
                .append(a.sender().name()).append(" -> ")
                .append(a.receiver().name()).append("\n"));

        sendEmail(from, fromName, from, "Organizador",
                "[Amigo Invisible] Sorteo - " + LocalDateTime.now().format(FORMATTER),
                body.toString());
    }
}
