package com.dgc.amigoinvisible.step.email;

import com.dgc.amigoinvisible.holder.AssignmentHolder;
import com.dgc.amigoinvisible.model.Assignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.Iterator;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class AssignmentItemReader implements ItemReader<Assignment> {

    private final AssignmentHolder assignmentHolder;

    private Iterator<Assignment> iterator;

    @Override
    public Assignment read() {
        if (iterator == null) {
            iterator = assignmentHolder.getAssignments().iterator();
            log.info("AssignmentItemReader initialized with {} assignments",
                    assignmentHolder.getAssignments().size());
        }
        return iterator.hasNext() ? iterator.next() : null;
    }
}
