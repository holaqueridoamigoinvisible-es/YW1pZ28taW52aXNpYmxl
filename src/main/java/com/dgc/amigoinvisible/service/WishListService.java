package com.dgc.amigoinvisible.service;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory wish-list store. Not persistent — data is lost on restart.
 * Thread-safe via ConcurrentHashMap.
 */
@Service
public class WishListService {

    private final ConcurrentHashMap<String, String> wishes = new ConcurrentHashMap<>();

    /**
     * Saves (or replaces) the wish list for the given email address.
     *
     * @param email the participant's email (normalised to lowercase/trimmed internally)
     * @param text  free-text wish list
     */
    public void save(String email, String text) {
        wishes.put(normalise(email), text);
    }

    /**
     * Returns the wish list for the given email, or empty if not yet set.
     */
    public Optional<String> get(String email) {
        return Optional.ofNullable(wishes.get(normalise(email)));
    }

    private static String normalise(String email) {
        return email == null ? "" : email.toLowerCase().trim();
    }
}
