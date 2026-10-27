package com.dgc.amigoinvisible.holder;

import com.dgc.amigoinvisible.model.Assignment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AssignmentHolder {

    private List<Assignment> assignments = new ArrayList<>();

    public List<Assignment> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<Assignment> assignments) {
        this.assignments = assignments;
    }
}
