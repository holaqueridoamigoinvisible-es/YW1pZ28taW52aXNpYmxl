package com.dgc.amigoinvisible.web;

import com.dgc.amigoinvisible.service.TokenService;
import com.dgc.amigoinvisible.service.WishListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Shows who a participant has to gift and lets them share their own wish list.
 * Token is stateless (AES-encrypted) — no DB read required.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/reveal")
public class RevealController {

    private final TokenService    tokenService;
    private final WishListService wishListService;

    /** Decodes the token and renders the reveal page. */
    @GetMapping("/{token}")
    public String reveal(@PathVariable String token,
                          @RequestParam(required = false) Boolean saved,
                          Model model) {
        return tokenService.decode(token)
                .map(data -> {
                    String receiverWishes = wishListService.get(data.receiverEmail()).orElse(null);
                    String myWishes       = wishListService.get(data.senderEmail()).orElse(null);
                    model.addAttribute("receiverName",   data.receiverName());
                    model.addAttribute("receiverWishes", receiverWishes);
                    model.addAttribute("myWishes",       myWishes);
                    model.addAttribute("token",          token);
                    model.addAttribute("saved",          saved);
                    return "reveal";
                })
                .orElseGet(() -> {
                    log.warn("RevealController: invalid or expired token, redirecting to /");
                    return "redirect:/";
                });
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
}
