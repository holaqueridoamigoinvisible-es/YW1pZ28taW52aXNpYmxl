package com.dgc.amigoinvisible.web;

import com.dgc.amigoinvisible.config.AmigoProperties;
import com.dgc.amigoinvisible.service.ChatService;
import com.dgc.amigoinvisible.service.EmailService;
import com.dgc.amigoinvisible.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * Shows who a participant has to gift and lets them share their own wish list.
 * Also serves the anonymous in-memory chat for both santa and recipient.
 * Token is stateless (AES-encrypted) — no DB read required.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/reveal")
public class RevealController {

    private final TokenService    tokenService;
    private final ChatService     chatService;
    private final EmailService    emailService;
    private final AmigoProperties amigoProperties;

    /** Decodes the token and renders the reveal page (role-aware). */
    @GetMapping("/{token}")
    public String reveal(@PathVariable String token,
                          @RequestParam(required = false) Boolean reminded,
                          Model model) {
        return tokenService.decode(token)
                .map(data -> {
                    String roomId = roomId(data);
                    model.addAttribute("role",     data.role());
                    model.addAttribute("token",    token);
                    model.addAttribute("messages", chatService.getMessages(roomId));
                    model.addAttribute("reminded", Boolean.TRUE.equals(reminded));

                    if ("RECIPIENT".equals(data.role())) {
                        model.addAttribute("recipientName", data.receiverName());
                    } else {
                        // SANTA (or legacy tokens defaulted to SANTA)
                        model.addAttribute("receiverName", data.receiverName());
                    }
                    return "reveal";
                })
                .orElseGet(() -> {
                    log.warn("RevealController: invalid or expired token, redirecting to /");
                    return "redirect:/";
                });
    }

    /** Sends a chat reminder email to the receiver and redirects back with reminded=true. */
    @PostMapping("/{token}/reminder")
    public String sendReminder(@PathVariable String token) {
        return tokenService.decode(token)
                .map(data -> {
                    if (!"SANTA".equals(data.role())) {
                        return "redirect:/";
                    }
                    String recipientToken = tokenService.createRecipientToken(
                            data.senderName(), data.senderEmail(),
                            data.receiverName(), data.receiverEmail(),
                            data.drawId());
                    String chatUrl = amigoProperties.getApp().getBaseUrl()
                            + "/reveal/" + recipientToken;
                    emailService.sendChatReminder(data.receiverEmail(), data.receiverName(), chatUrl);
                    return "redirect:/reveal/" + token + "?reminded=true";
                })
                .orElse("redirect:/");
    }

    /** Returns the chat messages for a room as JSON (polled every 5 s by the client). */
    @GetMapping("/{token}/messages")
    @ResponseBody
    public List<ChatService.ChatMessage> messages(@PathVariable String token) {
        return tokenService.decode(token)
                .map(data -> chatService.getMessages(roomId(data)))
                .orElse(List.of());
    }

    /** Appends a chat message to the room. */
    @PostMapping("/{token}/messages")
    @ResponseBody
    public ResponseEntity<Void> postMessage(@PathVariable String token,
                                             @RequestParam String text) {
        var decoded = tokenService.decode(token);
        if (decoded.isEmpty()) return ResponseEntity.<Void>badRequest().build();
        var data = decoded.get();
        chatService.addMessage(roomId(data), data.role(), text.trim());
        return ResponseEntity.<Void>ok().build();
    }

    /** RoomId shared by both tokens of a pair: drawId + ":" + santaEmail. */
    private static String roomId(TokenService.AssignmentData data) {
        return data.drawId() + ":" + data.senderEmail().toLowerCase().trim();
    }
}
