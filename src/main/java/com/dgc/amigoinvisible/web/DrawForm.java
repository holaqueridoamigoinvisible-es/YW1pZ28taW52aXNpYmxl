package com.dgc.amigoinvisible.web;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DrawForm {

    private List<ParticipantEntry> participants = new ArrayList<>();
    private List<ExclusionEntry>   exclusions   = new ArrayList<>();

    @Data
    public static class ParticipantEntry {
        private String name;
        private String email;
    }

    @Data
    public static class ExclusionEntry {
        private String sender;
        private String receiver;
    }
}
