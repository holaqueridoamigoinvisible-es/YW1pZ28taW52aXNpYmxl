package com.dgc.amigoinvisible.model;

/**
 * A forbidden sender→receiver pair: this assignment must not be generated.
 * Names must match exactly the names used in participants.csv.
 */
public record Exclusion(String sender, String receiver) {}
