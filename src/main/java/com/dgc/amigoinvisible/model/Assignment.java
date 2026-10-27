package com.dgc.amigoinvisible.model;

import java.io.Serializable;

public record Assignment(Person sender, Person receiver) implements Serializable {
}
