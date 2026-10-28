package com.dgc.amigoinvisible.web;

import com.dgc.amigoinvisible.config.AmigoProperties;
import com.dgc.amigoinvisible.model.Assignment;
import com.dgc.amigoinvisible.model.Exclusion;
import com.dgc.amigoinvisible.model.Person;
import com.dgc.amigoinvisible.service.DerangementService;
import com.dgc.amigoinvisible.service.EmailSendResult;
import com.dgc.amigoinvisible.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DrawController {

    private final DerangementService derangementService;
    private final EmailService emailService;
    private final AmigoProperties amigoProperties;

    @GetMapping("/")
    public String index(Model model) {
        log.info("GET / - loading form");
        DrawForm form = new DrawForm();
        for (int i = 0; i < 5; i++) {
            form.getParticipants().add(new DrawForm.ParticipantEntry());
        }
        form.getExclusions().add(new DrawForm.ExclusionEntry());
        model.addAttribute("form", form);
        model.addAttribute("title", "Amigo Invisible");
        return "index";
    }

    /**
     * Single-step: draw + async email send.
     * No HTTP session involved — avoids session loss on redeploy.
     */
    @PostMapping("/draw")
    public String draw(DrawForm form, Model model) {
        log.info("POST /draw - processing form");

        List<Person> persons = form.getParticipants().stream()
                .filter(p -> p.getName() != null && !p.getName().isBlank()
                          && p.getEmail() != null && !p.getEmail().isBlank())
                .map(p -> new Person(p.getName().trim(), p.getEmail().trim()))
                .collect(Collectors.toList());

        List<Exclusion> exclusions = form.getExclusions().stream()
                .filter(e -> e.getSender() != null && !e.getSender().isBlank()
                          && e.getReceiver() != null && !e.getReceiver().isBlank())
                .map(e -> new Exclusion(e.getSender().trim(), e.getReceiver().trim()))
                .collect(Collectors.toList());

        log.info("Participants: {}, Exclusions: {}", persons.size(), exclusions.size());

        // Validate email format for non-empty participants
        String emailRegex = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$";
        List<String> invalidEmails = form.getParticipants().stream()
                .filter(p -> p.getName() != null && !p.getName().isBlank()
                          && p.getEmail() != null && !p.getEmail().isBlank())
                .map(p -> p.getEmail().trim())
                .filter(email -> !email.matches(emailRegex))
                .collect(Collectors.toList());

        if (!invalidEmails.isEmpty()) {
            log.warn("Invalid emails found: {}", invalidEmails);
            model.addAttribute("form", form);
            model.addAttribute("title", "Amigo Invisible");
            model.addAttribute("error",
                    "Los siguientes emails no son válidos: " + String.join(", ", invalidEmails));
            return "index";
        }

        if (persons.size() < 2) {
            log.warn("Not enough participants: {}", persons.size());
            model.addAttribute("form", form);
            model.addAttribute("title", "Amigo Invisible");
            model.addAttribute("error", "Se necesitan al menos 2 participantes con nombre y email validos.");
            return "index";
        }

        try {
            List<Assignment> assignments = derangementService.draw(persons, exclusions);
            log.info("Draw completed - {} assignments, sending emails", assignments.size());
            EmailSendResult result = emailService.sendAssignmentEmails(assignments);
            log.info("Email send finished - sent={}, failed={}", result.sent(), result.failed());
            model.addAttribute("sent",         result.sent());
            model.addAttribute("failed",       result.failed());
            model.addAttribute("failedEmails", result.failedEmails());
            model.addAttribute("supportEmail", amigoProperties.getMail().getFrom());
            return "done";
        } catch (IllegalStateException ex) {
            log.error("Draw failed: {}", ex.getMessage());
            model.addAttribute("form", form);
            model.addAttribute("title", "Amigo Invisible");
            model.addAttribute("error", "No se pudo realizar el sorteo: " + ex.getMessage());
            return "index";
        }
    }
}
