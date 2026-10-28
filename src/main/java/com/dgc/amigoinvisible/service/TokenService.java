package com.dgc.amigoinvisible.service;

import com.dgc.amigoinvisible.config.AmigoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * Stateless token service: AES-128-CBC, random IV prepended, Base64URL (no padding).
 * Payload format: drawId|senderName|senderEmail|receiverName|receiverEmail|role
 * (legacy 5-field tokens without role are decoded as SANTA for backward compat)
 */
@Slf4j
@Service
public class TokenService {

    private final SecretKeySpec aesKey;

    public TokenService(AmigoProperties properties) {
        String secret = properties.getToken().getSecret();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] keyBytes = md.digest(secret.getBytes(StandardCharsets.UTF_8));
            this.aesKey = new SecretKeySpec(keyBytes, "AES");
            log.info("TokenService: AES-128 key initialised from configured secret");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable — cannot initialise TokenService", e);
        }
    }

    /**
     * Encrypts a SANTA-role token (the santa's reveal link).
     *
     * @return Base64URL-encoded string (no padding), containing 16-byte IV + ciphertext
     */
    public String createSantaToken(String senderName, String senderEmail,
                                    String receiverName, String receiverEmail,
                                    String drawId) {
        return encrypt(String.join("|", drawId, senderName, senderEmail,
                                   receiverName, receiverEmail, "SANTA"));
    }

    /**
     * Encrypts a RECIPIENT-role token (the recipient's anonymous chat link).
     *
     * @return Base64URL-encoded string (no padding), containing 16-byte IV + ciphertext
     */
    public String createRecipientToken(String senderName, String senderEmail,
                                        String receiverName, String receiverEmail,
                                        String drawId) {
        return encrypt(String.join("|", drawId, senderName, senderEmail,
                                   receiverName, receiverEmail, "RECIPIENT"));
    }

    private String encrypt(String payload) {
        try {
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, ivSpec);
            byte[] encrypted = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception e) {
            log.error("TokenService: token creation failed", e);
            throw new RuntimeException("Token creation failed", e);
        }
    }

    /**
     * Decodes and decrypts a token.
     * Accepts both the legacy 5-field format (role defaults to SANTA) and
     * the current 6-field format.
     *
     * @return Optional containing the decoded data, or empty on any error.
     */
    public Optional<AssignmentData> decode(String token) {
        try {
            byte[] combined   = Base64.getUrlDecoder().decode(token);
            if (combined.length < 17) return Optional.empty(); // IV(16) + ≥1 byte ciphertext

            byte[] iv         = Arrays.copyOfRange(combined, 0, 16);
            byte[] ciphertext = Arrays.copyOfRange(combined, 16, combined.length);

            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);
            byte[] decrypted = cipher.doFinal(ciphertext);

            String payload = new String(decrypted, StandardCharsets.UTF_8);
            // Split up to 6 fields; 5-field legacy tokens get role defaulted to SANTA
            String[] parts = payload.split("\\|", 6);
            if (parts.length < 5) return Optional.empty();

            String role = parts.length == 6 ? parts[5] : "SANTA";
            return Optional.of(new AssignmentData(parts[0], parts[1], parts[2], parts[3], parts[4], role));
        } catch (Exception e) {
            log.warn("TokenService: token decode failed — {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Decoded assignment data from a stateless token. */
    public record AssignmentData(
            String drawId,
            String senderName,
            String senderEmail,
            String receiverName,
            String receiverEmail,
            String role) {}
}
