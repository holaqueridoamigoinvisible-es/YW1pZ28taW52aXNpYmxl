package com.dgc.amigoinvisible.model;

import java.io.Serializable;

public record Person(String name, String email) implements Serializable {
}
