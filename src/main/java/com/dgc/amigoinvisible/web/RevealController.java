package com.dgc.amigoinvisible.web;

import com.dgc.amigoinvisible.service.ChatService;
import com.dgc.amigoinvisible.service.TokenService;
import com.dgc.amigoinvisible.service.WishListService;
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
    private final WishListService wishListService;
    private final ChatService     chatService;

    /** Decodes the token and renders the reveal page (role-aware). */
    @GetMapping("/{token}")
    public String reveal(@PathVariable String token,
                          @RequestParam(required = false) Boolean saved,
                          Model model) {
        return tokenService.decode(token)
                .map(data -> {
                    String roomId = roomId(data);
                    model.addAttribute("role",     data.role());
                    model.addAttribute("token",    token);
                    model.addAttribute("messages", chatService.getMessages(roomId));

                    if ("RECIPIENT".equals(data.role())) {
                        model.addAttribute("recipientName", data.receiverName());
                    } else {
                        // SANTA (or legacy tokens defaulted to SANTA)
                        String receiverWishes = wishListService.get(data.receiverEmail()).orElse(null);
                        String myWishes       = wishListService.get(data.senderEmail()).orElse(null);
                        model.addAttribute("receiverName",   data.receiverName());
                        model.addAttribute("receiverWishes", receiverWishes);
                        model.addAttribute("myWishes",       myWishes);
                        model.addAttribute("saved",          saved);
                    }
                    return "reveal";
                })
                .orElseGet(() -> {
                    log.warn("RevealController: invalid or expired token, redirecting to /");
                    return "redirect:/";
                });
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

    /** Saves the sender's wish list and redirects back to the reveal page. */
    @PostMapping("/{token}/wishes")
    public String saveWishes(@PathVariable String token,
                              @RequestParam String wishes) {
        return tokenService.decode(token)
                .map(data -> {
                    wishListService.save(data.senderEmail(), wishes);
                    log.info("RevealController: wish list saved for {}",
                             data.senderEmail().replaceAll("(?<=.{2}).(?=.*@)", "*"));
                    return "redirect:/reveal/" + token + "?saved=true";
                })
                .orElseGet(() -> {
                    log.warn("RevealController: invalid token in POST /wishes, redirecting to /");
                    return "redirect:/";
                });
    }

    /** RoomId shared by both tokens of a pair: drawId + ":" + santaEmail. */
    private static String roomId(TokenService.AssignmentData data) {
        return data.drawId() + ":" + data.senderEmail().toLowerCase().trim();
    }
}
