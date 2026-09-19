package com.lab.flametemp.solver;

/**
 * Input rejected before iteration starts: unknown fuel, non-positive or
 * non-finite equivalence ratio / inlet temperature. The stable type string is
 * surfaced to HTTP clients.
 */
public class InvalidJobInputException extends RuntimeException {

    private final String type;

    public InvalidJobInputException(String type, String message) {
        super(message);
        this.type = type;
    }

    public String type() {
        return type;
    }
}
