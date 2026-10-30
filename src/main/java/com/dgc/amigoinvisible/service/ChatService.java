package com.dgc.amigoinvisible.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Chat service backed by Upstash Redis (REST API).
 * Falls back to in-memory storage when redisUrl is not configured (local dev).
 * Messages are stored as pipe-separated strings: role|text|epochSeconds
 * and have a 7-day TTL in Redis.
 */
@Slf4j
@Service
public class ChatService {

    /** A single chat message in a room. */
    public record ChatMessage(String role, String text, LocalDateTime sentAt) {}

    private static final long TTL_SECONDS = 604_800L; // 7 days

    private final String redisUrl;
    private final String redisToken;
    private final HttpClient http = HttpClient.newHttpClient();

    /** In-memory fallback used when Redis is not configured. */
    private final ConcurrentHashMap<String, List<ChatMessage>> rooms = new ConcurrentHashMap<>();

    public ChatService(
            @Value("${upstash.redis.url}") String redisUrl,
            @Value("${upstash.redis.token}") String redisToken) {
        this.redisUrl   = redisUrl;
        this.redisToken = redisToken;
        if (redisUrl == null || redisUrl.isBlank()) {
            log.warn("ChatService: UPSTASH_REDIS_REST_URL not set — using in-memory fallback");
        } else {
            log.info("ChatService: Upstash Redis configured at {}", redisUrl);
        }
    }

    /**
     * Appends a message to the given room. Silently ignores blank text.
     *
     * @param roomId  the room identifier (drawId + ":" + santaEmail)
     * @param role    sender role: "SANTA" or "RECIPIENT"
     * @param text    message text (must be non-blank)
     */
    public void addMessage(String roomId, String role, String text) {
        if (text == null || text.isBlank()) return;

        if (redisUrl == null || redisUrl.isBlank()) {
            rooms.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>())
                 .add(new ChatMessage(role, text, LocalDateTime.now()));
            return;
        }

        String serialized = role + "|" + text.replace("|", " ") + "|" + Instant.now().getEpochSecond();
        try {
            // RPUSH: POST {url}/rpush/{key}/{value}  (value as path segment)
            HttpRequest rpushReq = HttpRequest.newBuilder()
                    .uri(URI.create(redisUrl + "/rpush/" + encodeKey(roomId) + "/" + encodeKey(serialized)))
                    .header("Authorization", "Bearer " + redisToken)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> rpushRes = http.send(rpushReq, HttpResponse.BodyHandlers.ofString());
            if (rpushRes.statusCode() != 200) {
                log.warn("ChatService: rpush failed status={} body={}", rpushRes.statusCode(), rpushRes.body());
            } else {
                log.info("ChatService: message stored in room={}", roomId);
            }

            // EXPIRE: POST {url}/expire/{key}/{seconds}
            HttpRequest expireReq = HttpRequest.newBuilder()
                    .uri(URI.create(redisUrl + "/expire/" + encodeKey(roomId) + "/" + TTL_SECONDS))
                    .header("Authorization", "Bearer " + redisToken)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> expireRes = http.send(expireReq, HttpResponse.BodyHandlers.ofString());
            if (expireRes.statusCode() != 200) {
                log.warn("ChatService: expire failed status={}", expireRes.statusCode());
            }
        } catch (Exception e) {
            log.error("ChatService: error storing message in Redis for roomId={}: {}", roomId, e.getMessage());
        }
    }

    /**
     * Returns all messages in the room, filtered to the last 7 days, in insertion order.
     * Returns an empty list on any error or when the room does not exist yet.
     */
    public List<ChatMessage> getMessages(String roomId) {
        if (redisUrl == null || redisUrl.isBlank()) {
            List<ChatMessage> list = rooms.get(roomId);
            return list == null ? List.of() : Collections.unmodifiableList(list);
        }

        try {
            // LRANGE: GET {url}/lrange/{key}/0/-1
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(redisUrl + "/lrange/" + encodeKey(roomId) + "/0/-1"))
                    .header("Authorization", "Bearer " + redisToken)
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("ChatService: lrange status={}", res.statusCode());

            if (res.statusCode() != 200) {
                log.warn("ChatService: lrange returned status={} body={} for roomId={}", res.statusCode(), res.body(), roomId);
                return List.of();
            }
            log.info("ChatService: lrange OK for room={} body={}", roomId, res.body());

            return parseMessages(res.body());
        } catch (Exception e) {
            log.error("ChatService: error reading messages from Redis for roomId={}: {}", roomId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Parses the Upstash response JSON:
     * {"result":["SANTA|text|epoch","RECIPIENT|text|epoch",...]}
     * Filters out messages older than 7 days.
     */
    private List<ChatMessage> parseMessages(String json) {
        List<ChatMessage> result = new ArrayList<>();
        long cutoff = Instant.now().getEpochSecond() - TTL_SECONDS;

        // Locate the result array: find "result":[ ... ]
        int resultIdx = json.indexOf("\"result\":");
        if (resultIdx < 0) return result;
        int arrayStart = json.indexOf('[', resultIdx);
        int arrayEnd   = json.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd <= arrayStart) return result;

        String arrayContent = json.substring(arrayStart + 1, arrayEnd).trim();
        if (arrayContent.isBlank()) return result;

        // Iterate over each JSON string element in the array
        int pos = 0;
        while (pos < arrayContent.length()) {
            int quoteStart = arrayContent.indexOf('"', pos);
            if (quoteStart < 0) break;

            // Find closing quote, honouring backslash escapes
            int quoteEnd = quoteStart + 1;
            while (quoteEnd < arrayContent.length()) {
                char c = arrayContent.charAt(quoteEnd);
                if (c == '\\') {
                    quoteEnd += 2; // skip the escaped character
                } else if (c == '"') {
                    break;
                } else {
                    quoteEnd++;
                }
            }
            if (quoteEnd >= arrayContent.length()) break;

            String raw = unescapeJson(arrayContent.substring(quoteStart + 1, quoteEnd));

            // Parse pipe-separated: role|text|epochSeconds
            String[] parts = raw.split("\\|", 3);
            if (parts.length == 3) {
                try {
                    long epoch = Long.parseLong(parts[2].trim());
                    if (epoch >= cutoff) {
                        LocalDateTime sentAt = LocalDateTime.ofEpochSecond(epoch, 0, ZoneOffset.UTC);
                        result.add(new ChatMessage(parts[0], parts[1], sentAt));
                    }
                } catch (NumberFormatException nfe) {
                    log.warn("ChatService: skipping message with invalid epoch in: {}", raw);
                }
            }

            pos = quoteEnd + 1;
        }

        return Collections.unmodifiableList(result);
    }

    /** URL-encodes a Redis key for use in a REST path segment. */
    private static String encodeKey(String key) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Escapes a string for embedding as a JSON string value. */
    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Unescapes the common JSON escape sequences from a parsed string token. */
    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
