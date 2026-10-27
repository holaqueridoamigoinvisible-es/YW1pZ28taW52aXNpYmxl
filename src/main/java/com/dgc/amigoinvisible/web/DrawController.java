package com.dgc.amigoinvisible.web;

import com.dgc.amigoinvisible.model.Assignment;
import com.dgc.amigoinvisible.model.Exclusion;
import com.dgc.amigoinvisible.model.Person;
import com.dgc.amigoinvisible.service.DerangementService;
import com.dgc.amigoinvisible.service.EmailService;
import jakarta.servlet.http.HttpSession;
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

    private static final String SESSION_KEY = "assignments";

    private final DerangementService derangementService;
    private final EmailService emailService;

    @GetMapping("/")
    public String index(Model model) {
        log.info("GET / — loading form");
        DrawForm form = new DrawForm();
        for (int i = 0; i < 5; i++) {
            form.getParticipants().add(new DrawForm.ParticipantEntry());
        }
        form.getExclusions().add(new DrawForm.ExclusionEntry());
        model.addAttribute("form", form);
        model.addAttribute("title", "Amigo Invisible");
        return "index";
    }

    @PostMapping("/draw")
    public String draw(DrawForm form, Model model, HttpSession session) {
        log.info("POST /draw — processing form");

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

        if (persons.size() < 2) {
            log.warn("Not enough participants: {}", persons.size());
            model.addAttribute("form", form);
            model.addAttribute("title", "Amigo Invisible");
            model.addAttribute("error", "Se necesitan al menos 2 participantes con nombre y email válidos.");
            return "index";
        }

        try {
            List<Assignment> assignments = derangementService.draw(persons, exclusions);
            log.info("Draw completed — {} assignments stored in session", assignments.size());
            session.setAttribute(SESSION_KEY, assignments);
            model.addAttribute("count", assignments.size());
            return "result";
        } catch (IllegalStateException ex) {
            log.error("Draw failed: {}", ex.getMessage());
            model.addAttribute("form", form);
            model.addAttribute("title", "Amigo Invisible");
            model.addAttribute("error", "No se pudo realizar el sorteo: " + ex.getMessage());
            return "index";
        }
    }

    @PostMapping("/send")
    public String send(HttpSession session) {
        log.info("POST /send — retrieving assignments from session");

        @SuppressWarnings("unchecked")
        List<Assignment> assignments = (List<Assignment>) session.getAttribute(SESSION_KEY);

        if (assignments == null) {
            log.warn("No assignments found in session, redirecting to /");
            return "redirect:/";
        }

        log.info("Sending emails for {} assignments", assignments.size());
        emailService.sendAssignmentEmails(assignments);
        session.removeAttribute(SESSION_KEY);
        log.info("Email sending complete, session cleared");
        return "done";
    }
}
