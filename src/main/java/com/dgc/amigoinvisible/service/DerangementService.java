package com.dgc.amigoinvisible.service;

import com.dgc.amigoinvisible.model.Assignment;
import com.dgc.amigoinvisible.model.Exclusion;
import com.dgc.amigoinvisible.model.Person;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DerangementService {

    private static final int MAX_ATTEMPTS = 1000;

    /**
     * Computes a valid random assignment where:
     * <ul>
     *   <li>No one is assigned to themselves.</li>
     *   <li>No forbidden sender→receiver pair appears.</li>
     *   <li>No two people are assigned to each other (no mutual pairs).</li>
     * </ul>
     */
    public List<Assignment> draw(List<Person> persons, List<Exclusion> exclusions) {

        Set<String> forbidden = exclusions.stream()
                .map(e -> e.sender() + "->" + e.receiver())
                .collect(Collectors.toSet());

        if (!forbidden.isEmpty()) {
            log.info("Applying {} exclusion(s): {}", forbidden.size(), forbidden);
        }

        // name → index in persons list; constant across shuffles so compute once
        Map<String, Integer> senderIndex = new HashMap<>();
        for (int i = 0; i < persons.size(); i++) {
            senderIndex.put(persons.get(i).name(), i);
        }

        List<Person> receivers = new ArrayList<>(persons);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Collections.shuffle(receivers);
            if (isValid(persons, receivers, forbidden, senderIndex)) {
                List<Assignment> assignments = new ArrayList<>();
                for (int i = 0; i < persons.size(); i++) {
                    assignments.add(new Assignment(persons.get(i), receivers.get(i)));
                }
                log.info("Valid assignment found after {} attempt(s)", attempt + 1);
                return assignments;
            }
        }

        throw new IllegalStateException(
                "Could not find a valid assignment after " + MAX_ATTEMPTS + " attempts. " +
                "Check that the exclusion list is not too restrictive.");
    }

    private boolean isValid(List<Person> senders, List<Person> receivers,
                            Set<String> forbidden, Map<String, Integer> senderIndex) {
        for (int i = 0; i < senders.size(); i++) {
            String senderName   = senders.get(i).name();
            String receiverName = receivers.get(i).name();

            // Rule 1: no self-assignment
            if (senderName.equals(receiverName)) return false;

            // Rule 2: no forbidden pairs from exclusions.csv
            if (forbidden.contains(senderName + "->" + receiverName)) return false;

            // Rule 3: no mutual pairs — if A→B then B must not →A
            Integer j = senderIndex.get(receiverName);
            if (j != null && receivers.get(j).name().equals(senderName)) return false;
        }
        return true;
    }
}
