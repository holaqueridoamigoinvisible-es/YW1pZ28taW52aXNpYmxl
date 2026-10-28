package com.dgc.amigoinvisible.service;

import com.dgc.amigoinvisible.config.AmigoProperties;
import com.dgc.amigoinvisible.model.Assignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;

@Slf4j
@Service
public class EmailService {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GMAIL_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final AmigoProperties amigoProperties;
    private final HttpClient http = HttpClient.newHttpClient();

    public EmailService(
            @Value("${gmail.client-id}")     String clientId,
            @Value("${gmail.client-secret}") String clientSecret,
            @Value("${gmail.refresh-token}") String refreshToken,
            AmigoProperties amigoProperties) {
        this.clientId      = clientId;
        this.clientSecret  = clientSecret;
        this.refreshToken  = refreshToken;
        this.amigoProperties = amigoProperties;
        log.info("EmailService: Gmail API client ready");
    }

    public EmailSendResult sendAssignmentEmails(List<Assignment> assignments) {
        String drawId = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        log.info("EmailService: started draw={}, {} assignments", drawId, assignments.size());
        try {
            String accessToken = getAccessToken();
            log.info("EmailService: access token obtained");

            String from       = amigoProperties.getMail().getFrom();
            String fromName   = amigoProperties.getMail().getFromName();
            String adminEmail = from; // resumen va a la misma cuenta de envío

            int sent = 0, failed = 0;
            List<String> failedEmails = new ArrayList<>();
            for (Assignment a : assignments) {
                String body = "Hola " + a.sender().name() + ",\n\n"
                        + "¡Tu amigo invisible es " + a.receiver().name() + "!\n\n"
                        + "¡Mucha suerte con el regalo!\n\nAmigo Invisible App";

                if (!domainHasMx(a.sender().email())) {
                    log.error("EmailService: domain has no MX records, skipping {}", maskEmail(a.sender().email()));
                    failedEmails.add(maskEmail(a.sender().email()));
                    failed++;
                } else if (sendEmail(accessToken, from, fromName, a.sender().email(),
                        "Amigo invisible - Tu asignación  (id. " + drawId + ")", body)) {
                    log.info("EmailService: sent OK to {}", maskEmail(a.sender().email()));
                    sent++;
                } else {
                    log.error("EmailService: FAILED to {}", maskEmail(a.sender().email()));
                    failedEmails.add(maskEmail(a.sender().email()));
                    failed++;
                }
            }
            log.info("EmailService: done draw={} - sent={}, failed={}", drawId, sent, failed);
            sendAdminSummary(accessToken, from, fromName, adminEmail, assignments, sent, failed, drawId);
            return new EmailSendResult(drawId, sent, failed, failedEmails);
        } catch (Exception e) {
            log.error("EmailService: error draw={}", drawId, e);
            List<String> allFailed = assignments.stream()
                    .map(a -> maskEmail(a.sender().email()))
                    .toList();
            return new EmailSendResult(drawId, 0, assignments.size(), allFailed);
        }
    }

    private String getAccessToken() throws Exception {
        String body = "client_id="     + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&refresh_token=" + enc(refreshToken)
                + "&grant_type=refresh_token";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        log.info("EmailService: token response status={}", res.statusCode());

        String responseBody = res.body();
        int idx = responseBody.indexOf("\"access_token\":");
        if (idx < 0) throw new RuntimeException("No access_token in response: " + responseBody);
        int start = responseBody.indexOf("\"", idx + 15) + 1;
        int end   = responseBody.indexOf("\"", start);
        return responseBody.substring(start, end);
    }

    private boolean sendEmail(String accessToken, String fromAddr, String fromName,
                               String to, String subject, String text) {
        try {
            String mime = "From: " + fromName + " <" + fromAddr + ">\r\n"
                    + "To: " + to + "\r\n"
                    + "Subject: " + subject + "\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n"
                    + "\r\n"
                    + text;

            String encoded = Base64.getUrlEncoder().encodeToString(mime.getBytes(StandardCharsets.UTF_8));
            String json = "{\"raw\":\"" + encoded + "\"}";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(GMAIL_URL))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("EmailService: Gmail status={} body={}", res.statusCode(), res.body());
            return res.statusCode() == 200;
        } catch (Exception e) {
            log.error("EmailService: error sending to {}: {}", maskEmail(to), e.getMessage());
            return false;
        }
    }

    private void sendAdminSummary(String accessToken, String from, String fromName,
                                   String adminEmail, List<Assignment> assignments,
                                   int sent, int failed, String drawId) {
        StringBuilder body = new StringBuilder();
        body.append("ID sorteo: ").append(drawId).append("\n");
        body.append("Sorteo realizado el ").append(LocalDateTime.now().format(FORMATTER)).append("\n");
        body.append("Participantes: ").append(assignments.size()).append("\n");
        body.append("Emails enviados: ").append(sent);
        if (failed > 0) body.append("  |  Fallos: ").append(failed);
        body.append("\n\nAsignaciones:\n");
        assignments.forEach(a -> body.append("  ")
                .append(a.sender().name()).append(" -> ")
                .append(a.receiver().name()).append(" ").append(a.receiver().email()).append("\n"));

        sendEmail(accessToken, from, fromName, adminEmail,
                "[Amigo Invisible] Sorteo #" + drawId + " - " + LocalDateTime.now().format(FORMATTER),
                body.toString());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Returns true if the email's domain has at least one MX record. */
    private static boolean domainHasMx(String email) {
        try {
            String domain = email.substring(email.indexOf('@') + 1);
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            Attributes attrs = new InitialDirContext(env)
                    .getAttributes(domain, new String[]{"MX"});
            return attrs.get("MX") != null;
        } catch (Exception e) {
            log.warn("EmailService: MX lookup failed for {}: {}", email, e.getMessage());
            return false;
        }
    }

    /** Returns the first 2 chars of the local part + *** + @domain. e.g. mario.gomez@gmail.com → ma***@gmail.com */
    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        String local  = email.substring(0, at);
        String domain = email.substring(at);          // includes the '@'
        String prefix = local.length() >= 2 ? local.substring(0, 2) : local;
        return prefix + "***" + domain;
    }
}
