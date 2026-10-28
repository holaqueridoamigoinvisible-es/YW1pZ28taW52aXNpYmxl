package com.dgc.amigoinvisible.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory anonymous chat service.
 * Messages are keyed by roomId (drawId + ":" + santaEmail) and lost on restart — by design.
 * CopyOnWriteArrayList keeps reads lock-free; writes (chat messages) are rare.
 */
@Service
public class ChatService {

    /** A single chat message in a room. */
    public record ChatMessage(String role, String text, LocalDateTime sentAt) {}

    private final ConcurrentHashMap<String, List<ChatMessage>> rooms = new ConcurrentHashMap<>();

    /**
     * Appends a message to the given room. Silently ignores blank text.
     *
     * @param roomId  the room identifier (drawId + ":" + santaEmail)
     * @param role    sender role: "SANTA" or "RECIPIENT"
     * @param text    message text (must be non-blank)
     */
    public void addMessage(String roomId, String role, String text) {
        if (text == null || text.isBlank()) return;
        rooms.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>())
             .add(new ChatMessage(role, text, LocalDateTime.now()));
    }

    /**
     * Returns an unmodifiable view of all messages in the room,
     * or an empty list if the room does not exist yet.
     */
    public List<ChatMessage> getMessages(String roomId) {
        List<ChatMessage> list = rooms.get(roomId);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }
}
