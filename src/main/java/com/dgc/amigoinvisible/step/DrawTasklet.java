package com.dgc.amigoinvisible.step;

import com.dgc.amigoinvisible.config.AmigoProperties;
import com.dgc.amigoinvisible.holder.AssignmentHolder;
import com.dgc.amigoinvisible.model.Assignment;
import com.dgc.amigoinvisible.model.Exclusion;
import com.dgc.amigoinvisible.model.Person;
import com.dgc.amigoinvisible.service.DerangementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DrawTasklet implements Tasklet {

    private final DerangementService derangementService;
    private final AssignmentHolder assignmentHolder;
    private final AmigoProperties amigoProperties;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        List<Person> persons = readCsv(
                amigoProperties.getParticipants().getFile(),
                line -> {
                    String[] p = line.split(",", 2);
                    return new Person(p[0].trim(), p[1].trim());
                });

        if (persons.size() < 2) {
            throw new IllegalStateException("At least 2 participants required; found: " + persons.size());
        }
        log.info("Read {} participants", persons.size());

        List<Exclusion> exclusions = readCsv(
                amigoProperties.getExclusions().getFile(),
                line -> {
                    String[] p = line.split(",", 2);
                    return new Exclusion(p[0].trim(), p[1].trim());
                });
        log.info("Read {} exclusion(s)", exclusions.size());

        List<Assignment> assignments = derangementService.draw(persons, exclusions);
        assignmentHolder.setAssignments(assignments);

        log.info("=== Resultado del sorteo ===");
        assignments.forEach(a -> log.info("  {} --> {}", a.sender().name(), a.receiver().name()));

        return RepeatStatus.FINISHED;
    }

    /**
     * Generic CSV reader: skips the header line and blank lines,
     * then applies {@code mapper} to each data line.
     */
    private <T> List<T> readCsv(String location, CsvMapper<T> mapper) throws IOException {
        List<T> result = new ArrayList<>();
        Resource resource = new DefaultResourceLoader().getResource(location);

        if (!resource.exists()) {
            log.warn("CSV file not found, skipping: {}", location);
            return result;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; } // skip header
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                try {
                    result.add(mapper.map(line));
                } catch (Exception e) {
                    log.warn("Skipping malformed line [{}]: {}", line, e.getMessage());
                }
            }
        }
        return result;
    }

    @FunctionalInterface
    private interface CsvMapper<T> {
        T map(String line) throws Exception;
    }
}
